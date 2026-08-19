package com.aniko.data.notification

import com.aniko.data.api.NotificationApi
import com.aniko.data.mapper.toDomain
import kotlinx.coroutines.CancellationException

/**
 * Опрос ленты уведомлений и показ локальных OS-уведомлений о новом (P10.T6).
 *
 * **Почему опрос, а не пуш.** Вердикт P10.T4: `auth/firebase` отдаёт имя FCM-топика внутри
 * Firebase-проекта САМОГО Anixart, публикуемого их серверными кредами; сторонний клиент подписаться
 * на него не может, а на iOS не может тем более (APNs-ключ выписан на их Apple Team и bundle id).
 * Единственный оставшийся честный механизм — периодически спрашивать сервер самим.
 *
 * **Цена решения, которую надо понимать.** Задержка уведомления равна периоду тика:
 * на Android — минимум 15 минут (жёсткий минимум `PeriodicWorkRequest`), на iOS — недетерминирована
 * (`BGAppRefreshTask` не даёт вообще никаких гарантий частоты и может не запуститься сутками),
 * на Desktop — 15 минут, но только пока приложение открыто. Мгновенных уведомлений здесь не будет
 * ни при каких доработках этого класса.
 *
 * **Порядок работы одного прохода:**
 * 1. нет разрешения на показ → выходим сразу, не потратив ни одного запроса;
 * 2. `notification/count` — дешёвый предохранитель: если счётчик не вырос с прошлого раза, вторая
 *    (тяжёлая) страница не запрашивается вообще;
 * 3. `notification/all/0` → дифф по id против [NotificationSyncStore.lastNotifiedId] ([selectUnseen]);
 * 4. показ, затем сдвиг курсора.
 *
 * Осознанный пробел шага 2: если пользователь ОДНОВРЕМЕННО получил новое уведомление и прочитал
 * старое в официальном клиенте, `count` не изменится и новое уведомление будет пропущено до
 * следующего роста счётчика. Альтернатива — тянуть страницу всегда — удваивает трафик и расход
 * батареи на каждом тике ради этого стыка; выбран счётчик (то же решение зафиксировано в плане
 * P10.T6).
 */
class NotificationPoller(
    private val api: NotificationApi,
    private val store: NotificationSyncStore,
    private val presenter: LocalNotificationPresenter,
    private val contentFactory: NotificationContentFactory,
) {
    /** Чем закончился проход. Возвращается наружу только ради тестов и читаемости логов. */
    sealed interface PollResult {
        /** Платформа не разрешает показывать уведомления (см. [LocalNotificationPresenter.ensurePermission]). */
        data object NotPermitted : PollResult

        /** Счётчик не вырос — ленту не запрашивали. */
        data object UpToDate : PollResult

        /**
         * Лента прочитана. [shown] может быть `0` и при непустой ленте — так выглядит самый первый
         * проход на устройстве (курсор только инициализируется, см. [selectUnseen]).
         */
        data class Delivered(
            val shown: Int,
        ) : PollResult
    }

    suspend fun poll(): PollResult {
        if (!presenter.ensurePermission()) return PollResult.NotPermitted

        val count = api.count().count
        val previousCount = store.lastCount
        store.lastCount = count
        val upToDate = previousCount != null && count <= previousCount

        return if (upToDate) {
            PollResult.UpToDate
        } else {
            deliver()
        }
    }

    private suspend fun deliver(): PollResult.Delivered {
        val page = api.all(FIRST_PAGE).content.map { it.toDomain() }
        val unseen = selectUnseen(page, store.lastNotifiedId)

        var shown = 0
        for (notification in unseen) {
            val content = contentFactory.create(notification) ?: continue
            presenter.show(content)
            shown++
        }

        // Курсор двигаем ПОСЛЕ показа: если процесс умрёт посередине, уведомление придёт ещё раз,
        // а не потеряется. Повтор безвреден — id уведомления стабилен, и платформа заменит уже
        // висящее в шторке вместо создания дубля (см. KDoc `LocalNotification.id`).
        // Максимум берётся по ВСЕЙ странице, а не по показанным, см. [highestIdOrNull].
        highestIdOrNull(page)?.let { highest ->
            store.lastNotifiedId = maxOf(highest, store.lastNotifiedId ?: highest)
        }

        return PollResult.Delivered(shown)
    }

    /**
     * [poll] в варианте «не роняет вызывающего» — полный аналог
     * [com.aniko.data.sync.drainSafely] и по той же причине: единственный вызывающий
     * ([com.aniko.data.sync.PeriodicSyncTask]) — фоновый тик, который не должен умирать из-за
     * одной неудачной итерации (сеть отвалилась, сервер отдал мусор, платформа отказала в показе).
     *
     * [CancellationException] перебрасывается явно — иначе отмена scope перестала бы
     * останавливать фоновый цикл на Desktop.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun pollSafely(): PollResult? =
        try {
            poll()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unexpected: Exception) {
            null
        }

    private companion object {
        const val FIRST_PAGE = 0
    }
}
