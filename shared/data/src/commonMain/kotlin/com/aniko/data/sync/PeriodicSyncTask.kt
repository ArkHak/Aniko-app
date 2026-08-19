package com.aniko.data.sync

import com.aniko.data.notification.NotificationPoller
import com.aniko.data.session.SessionState
import com.aniko.data.session.SessionStore

/**
 * Содержимое ОДНОГО срабатывания периодической фоновой задачи (P10.T1 + P10.T6).
 *
 * Появился, когда к дренажу офлайн-очереди добавился опрос уведомлений: у обеих работ один и тот
 * же триггер, и заводить под уведомления второй планировщик было бы прямым вредом — на Android это
 * означало бы второй `PeriodicWorkRequest`, то есть вдвое больше пробуждений процесса ради двух
 * запросов, которые прекрасно делаются подряд в одном. Поэтому платформенные реализации
 * [BackgroundSyncScheduler] дёргают этот класс, а не [SyncQueueWorker] напрямую.
 *
 * Порядок внутри тика — сначала очередь, потом уведомления — не случаен: дренаж отправляет на
 * сервер действия пользователя (отметки просмотра, изменения списков), и терять их из-за того, что
 * опрос уведомлений упал первым, нельзя.
 *
 * [SessionStore.bootstrap] первой строкой — не украшение, а условие работоспособности всего тика в
 * фоне. `SessionStore` стартует в [SessionState.Loading] и читает токен из secure storage только
 * по явному вызову `bootstrap()`, который делает корневой composable в `App.kt`. Фоновое
 * срабатывание на Android поднимает процесс БЕЗ единой Activity (`WorkManager` создаёт только
 * `Application`), то есть `App.kt` не выполняется никогда — а `TokenProvider.token()` в этот момент
 * ждёт выхода из `Loading` и, значит, ждал бы вечно, пока `WorkManager` не прибил бы воркер по
 * таймауту. `bootstrap()` идемпотентен, поэтому вызов при уже поднятом UI — no-op.
 */
class PeriodicSyncTask(
    private val sessionStore: SessionStore,
    private val worker: SyncQueueWorker,
    private val notificationPoller: NotificationPoller,
) {
    /**
     * @return то же, что и [drainSafely] до появления этого класса: результат дренажа очереди либо
     * `null`, если проход упал с неожиданным исключением. Платформенные обёртки маппят его в свои
     * коды (`ListenableWorker.Result` на Android, `setTaskCompletedWithSuccess` на iOS), и их
     * маппинг менять не пришлось. Результат опроса уведомлений наружу не выносится: он ни на что в
     * планировщике не влияет и никогда не должен приводить к ретраю всего тика.
     */
    suspend fun runOnce(): SyncQueueWorker.DrainResult? {
        sessionStore.bootstrap()
        if (sessionStore.sessionState.value !is SessionState.Authorized) {
            // Без токена и очередь, и лента уведомлений одинаково бессмысленны. Возвращаем
            // Unauthorized, а не null: платформенные обёртки уже трактуют его как «повторять
            // незачем, это не сбой» (см. `SyncQueueDrainWorker`), а null у них означает
            // неожиданное исключение и ретрай.
            return SyncQueueWorker.DrainResult.Unauthorized
        }

        val drainResult = worker.drainSafely()
        notificationPoller.pollSafely()
        return drainResult
    }
}
