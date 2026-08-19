package com.aniko.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration

/**
 * [SyncQueueWorker.drain] в варианте «не роняет вызывающего».
 *
 * Сам `drain()` уже ловит [com.aniko.model.AnixError] внутри и превращает его в
 * [SyncQueueWorker.DrainResult] — но не защищает от исключений уровня хранилища (SQL-ошибка в
 * `dueOperations`/`recordAttempt`) и от чего угодно неучтённого. Оба вызывающих — долгоживущие:
 * коллектор connectivity в [SyncCoordinator] и [PeriodicSyncTask] (его, в свою очередь, зовут
 * `delay`-цикл [runPeriodicSyncLoop] на Desktop, `CoroutineWorker` на Android и `BGAppRefreshTask`
 * на iOS). Ни один из них не должен умирать навсегда из-за одной неудачной итерации, поэтому
 * исключение здесь гасится и превращается в `null`.
 *
 * [CancellationException] перебрасывается явно: её проглатывание сломало бы структурированную
 * конкурентность (отмена scope перестала бы останавливать цикл).
 *
 * @return результат прохода или `null`, если проход упал с неожиданным исключением.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend fun SyncQueueWorker.drainSafely(): SyncQueueWorker.DrainResult? =
    try {
        drain()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unexpected: Exception) {
        null
    }

/**
 * Периодический `delay`-цикл поверх [PeriodicSyncTask] — реализация [BackgroundSyncScheduler] для
 * Desktop (P10.T1/P10.T6) и единственная его часть, которую можно покрыть обычным `commonTest` с
 * виртуальным временем, поэтому она живёт в `commonMain`, а не в `desktopMain`.
 *
 * Первый проход делается ПОСЛЕ первого [interval], а не сразу: стартовый дренаж уже обеспечен
 * переходом [ConnectivityStatus.Unknown] → [ConnectivityStatus.Online] в [SyncCoordinator],
 * дублировать его здесь незачем.
 *
 * Функция не завершается сама — выходит только по отмене корутины.
 */
internal suspend fun runPeriodicSyncLoop(
    task: PeriodicSyncTask,
    interval: Duration = BackgroundSyncScheduler.MIN_PERIODIC_INTERVAL,
) {
    while (currentCoroutineContext().isActive) {
        delay(interval)
        task.runOnce()
    }
}
