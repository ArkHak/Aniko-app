package com.aniko.data.notification

import com.aniko.model.AppNotification

/**
 * Сколько уведомлений максимум показать за один тик.
 *
 * Не «сколько влезет»: между двумя тиками может пройти сколько угодно времени (на iOS
 * `BGAppRefreshTask` может не запуститься неделями, см. `IosBackgroundSyncScheduler`), и без
 * потолка пользователь получил бы разом всю первую страницу — 25 звуков подряд. Пять — верхняя
 * граница, после которой шторка перестаёт читаться.
 */
internal const val MAX_NOTIFICATIONS_PER_TICK = 5

/**
 * Дифф «что из этой страницы пользователь ещё не видел» (P10.T6).
 *
 * Чистая функция без ввода-вывода — вся содержательная логика поллинга сосредоточена здесь,
 * чтобы её можно было покрыть обычным `commonTest` без фейков сети и хранилища
 * (`NotificationDiffTest`).
 *
 * Сравнение идёт по **id**, а не по `timestamp` и не по флагу `is_new`:
 * - `timestamp` у разных подтипов уведомлений проставляется по-разному (у `episode` — время
 *   выхода серии, а не время появления уведомления), сортировка по нему дала бы неверный порядок;
 * - `is_new` сервер сбрасывает на `notification/read`, то есть он отвечает на вопрос «прочитал ли
 *   пользователь в официальном клиенте», а не «показывали ли МЫ» (см. [NotificationSyncStore]).
 *
 * @param lastSeenId `null` — поллинг на этом устройстве ещё ни разу не отрабатывал. Тогда
 * возвращается пустой список: первый запуск обязан только запомнить текущий максимум, а не
 * вывалить в шторку всю историю аккаунта. Это самый важный кейс этой функции.
 * @param limit потолок, по умолчанию [MAX_NOTIFICATIONS_PER_TICK]. Отбрасываются САМЫЕ СТАРЫЕ из
 * новых — при переполнении полезнее увидеть свежее.
 * @return новые уведомления в порядке от старых к новым, чтобы самое свежее оказалось в шторке
 * сверху (последним показанным).
 */
internal fun selectUnseen(
    notifications: List<AppNotification>,
    lastSeenId: Long?,
    limit: Int = MAX_NOTIFICATIONS_PER_TICK,
): List<AppNotification> {
    if (lastSeenId == null) return emptyList()
    return notifications
        .filter { it.id > lastSeenId }
        .sortedBy { it.id }
        .takeLast(limit)
}

/**
 * Новое значение «последнего виденного id» после обработки страницы.
 *
 * Считается по ВСЕЙ странице, а не по показанным [selectUnseen]: отброшенные потолком
 * [MAX_NOTIFICATIONS_PER_TICK] уведомления не должны всплыть снова на следующем тике — они уже
 * устарели, и повторно они не станут интереснее.
 *
 * @return `null`, если страница пуста и двигать курсор не от чего.
 */
internal fun highestIdOrNull(notifications: List<AppNotification>): Long? = notifications.maxOfOrNull { it.id }
