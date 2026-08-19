package com.aniko.data.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * iOS-реализация [BackgroundSyncScheduler] на `BGTaskScheduler`/`BGAppRefreshTask` (P10.T1).
 *
 * **Что здесь платформа, а не наш выбор.** `BGAppRefreshTaskRequest` — одноразовый: система
 * выполняет его РОВНО один раз, «периодичности» в API нет вообще. Единственный поддерживаемый
 * способ получить повторяемость — пере-подать запрос из обработчика предыдущего запуска, что и
 * делает [handleRefresh] ПЕРВЫМ действием (до дренажа): если сделать это в конце и дренаж упадёт
 * или система убьёт задачу по таймауту, цепочка оборвётся навсегда до следующего ручного запуска
 * приложения. [BackgroundSyncScheduler.MIN_PERIODIC_INTERVAL] здесь задаёт `earliestBeginDate` —
 * «не раньше чем», а не «каждые»; реальный момент выбирает система по своим эвристикам
 * (заряд, частота использования приложения, Low Power Mode), и приложение может не получить
 * фонового времени вообще.
 *
 * **Два обязательных условия со стороны Xcode-проекта**, без которых это не работает и падает:
 * 1. `BGTaskSchedulerPermittedIdentifiers` в `iosApp/iosApp/Info.plist` должен содержать
 *    [BackgroundSyncScheduler.SYNC_TASK_IDENTIFIER] — иначе `submitTaskRequest` вернёт ошибку
 *    `BGTaskSchedulerErrorCodeNotPermitted`;
 * 2. `UIBackgroundModes` должен содержать `fetch` — иначе система никогда не запустит задачу.
 *
 * [registerHandlers] обязан быть вызван до конца `didFinishLaunchingWithOptions` (иначе
 * `NSInternalInconsistencyException` при первом же запуске) — поэтому его зовёт `AppDelegate` из
 * Swift, а не [SyncCoordinator.start]; см. KDoc [BackgroundSyncScheduler.registerHandlers].
 *
 * @param scope долгоживущий scope уровня приложения — тот же, что у [SyncCoordinator].
 */
class IosBackgroundSyncScheduler(
    private val task: PeriodicSyncTask,
    private val scope: CoroutineScope,
) : BackgroundSyncScheduler {
    private val scheduler = BGTaskScheduler.sharedScheduler

    override fun registerHandlers() {
        scheduler.registerForTaskWithIdentifier(
            identifier = BackgroundSyncScheduler.SYNC_TASK_IDENTIFIER,
            // null = системная фоновая очередь по умолчанию (не главная). Своя очередь не нужна:
            // обработчик только стартует корутину и сразу возвращается, вся работа идёт в [scope].
            usingQueue = null,
        ) { bgTask -> (bgTask as? BGAppRefreshTask)?.let(::handleRefresh) }
    }

    override fun schedulePeriodicSync() {
        submitRefreshRequest()
    }

    override fun cancelPeriodicSync() {
        scheduler.cancelTaskRequestWithIdentifier(BackgroundSyncScheduler.SYNC_TASK_IDENTIFIER)
    }

    /**
     * Обработчик одного фонового запуска.
     *
     * `expirationHandler` обязателен: если система решит, что времени больше нет, а мы не завершим
     * задачу сами, приложение получит штраф в виде снижения приоритета будущих фоновых запусков.
     * Отменяем корутину и честно репортим неуспех.
     */
    private fun handleRefresh(bgTask: BGAppRefreshTask) {
        // Первым делом — заявка на следующий запуск, см. KDoc класса.
        submitRefreshRequest()

        // `job` заводится ДО `setExpirationHandler`, а не наоборот: `launch` возвращает `Job`
        // синхронно (сама корутина стартует асинхронно, но ссылка на неё готова сразу), поэтому
        // обработчику некогда увидеть её как `null` — в отличие от обратного порядка, где
        // теоретическое окно между "обработчик уже зарегистрирован" и "job присвоен" существовало.
        val job =
            scope.launch {
                val result = task.runOnce()
                bgTask.setTaskCompletedWithSuccess(result != null)
            }
        bgTask.setExpirationHandler {
            job.cancel()
            bgTask.setTaskCompletedWithSuccess(false)
        }
    }

    /**
     * `submitTaskRequest` возвращает `false` и заполняет `NSError`, если заявку не приняли.
     * Единственная реалистичная причина отказа — неверная конфигурация `Info.plist` (см. KDoc
     * класса, `BGTaskSchedulerErrorCodeNotPermitted`), то есть ошибка сборки, а не рантайма.
     * Поэтому `NSError` не запрашивается (`error = null`) и результат намеренно игнорируется:
     * падать или шуметь в UI из-за этого незачем — офлайн-очередь всё равно дренируется при
     * переходе offline→online ([SyncCoordinator]) и при следующем запуске приложения, фоновая
     * задача здесь страховка, а не единственный путь.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun submitRefreshRequest() {
        val request = BGAppRefreshTaskRequest(BackgroundSyncScheduler.SYNC_TASK_IDENTIFIER)
        request.earliestBeginDate =
            NSDate.dateWithTimeIntervalSinceNow(
                BackgroundSyncScheduler.MIN_PERIODIC_INTERVAL.inWholeSeconds.toDouble(),
            )
        scheduler.submitTaskRequest(request, error = null)
    }
}
