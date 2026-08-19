package com.aniko.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * `WorkManager`-обёртка вокруг [PeriodicSyncTask] (P10.T1 + P10.T6).
 *
 * Тонкая: и проход по очереди (backoff, классификация ошибок, FIFO), и опрос уведомлений живут в
 * `commonMain` — здесь только маппинг результата в `ListenableWorker.Result` и доставание
 * синглтона из Koin. Отдельного воркера под уведомления нет намеренно: это был бы второй
 * `PeriodicWorkRequest`, то есть вдвое больше пробуждений процесса, см. KDoc [PeriodicSyncTask].
 *
 * Зависимость берётся через [KoinComponent], а не через конструктор: `WorkManager` создаёт
 * воркеры сам через рефлексию по `(Context, WorkerParameters)`, а заводить `WorkerFactory` +
 * кастомную `Configuration.Provider` ради одной зависимости — лишний слой (пришлось бы отключать
 * автоинициализацию WorkManager через `androidx.startup` и держать это в `AnixApplication`).
 *
 * Маппинг результата:
 * - [SyncQueueWorker.DrainResult.Backoff] → `retry()`: осталось что-то, что имеет смысл повторить
 *   раньше следующего периода. Собственный `nextAttemptAt` операции при этом уже записан, так что
 *   слишком ранний повтор просто не заберёт её из `dueOperations` — двойного backoff не будет.
 * - [SyncQueueWorker.DrainResult.Unauthorized] → `success()`, а НЕ `retry()`: без нового логина
 *   повтор ничего не изменит, ретраить 401 в цикле — только жечь батарею.
 * - `null` (неожиданное исключение, см. [drainSafely]) → `retry()`.
 */
internal class SyncQueueDrainWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters),
    KoinComponent {
    private val periodicSyncTask: PeriodicSyncTask by inject()

    override suspend fun doWork(): Result =
        when (periodicSyncTask.runOnce()) {
            is SyncQueueWorker.DrainResult.Backoff -> Result.retry()
            null -> Result.retry()
            else -> Result.success()
        }
}
