package com.aniko.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/** Имя unique-work периодической задачи (P10.T1). */
private const val PERIODIC_WORK_NAME = BackgroundSyncScheduler.SYNC_TASK_IDENTIFIER

/** Имя unique-work разовой задачи «дренировать как только появится сеть». */
private const val IMMEDIATE_WORK_NAME = BackgroundSyncScheduler.SYNC_TASK_IDENTIFIER + ".immediate"

/**
 * Android-реализация [BackgroundSyncScheduler] на `WorkManager` (P10.T1).
 *
 * Две задачи вместо одной:
 * - **периодическая** ([BackgroundSyncScheduler.MIN_PERIODIC_INTERVAL] = 15 минут — минимум,
 *   который вообще принимает `PeriodicWorkRequest`; меньшие значения система молча поднимает до
 *   15 минут, обходить это не пытаемся) — страховка «очередь не должна простаивать бесконечно»;
 * - **разовая** на каждом старте координатора — чтобы не ждать до 15 минут первого срабатывания
 *   периодической. `ExistingWorkPolicy.REPLACE`: если предыдущая ещё висит неотработанной из-за
 *   отсутствия сети, смысла копить их несколько нет, нужен ровно один дренаж.
 *
 * `NetworkType.CONNECTED` на обеих: без сети `drain()` гарантированно получит retryable-ошибку и
 * только зря разбудит процесс. Реакция на ПОЯВЛЕНИЕ сети раньше периода — не через WorkManager, а
 * через [SyncCoordinator] + [AndroidConnectivityMonitor] (P10.T2): пока приложение живо, это
 * заметно отзывчивее, чем ждать, когда система соблаговолит выполнить constraint.
 *
 * `ExistingPeriodicWorkPolicy.KEEP` для периодической: перезапуск приложения не должен сбрасывать
 * уже отсчитанный интервал (`UPDATE`/`CANCEL_AND_REENQUEUE` при частых запусках привели бы к
 * тому, что задача не выполнилась бы никогда).
 */
class AndroidBackgroundSyncScheduler(
    context: Context,
) : BackgroundSyncScheduler {
    private val appContext = context.applicationContext

    private val constraints =
        Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    override fun schedulePeriodicSync() {
        val workManager = WorkManager.getInstance(appContext)

        val periodic =
            PeriodicWorkRequestBuilder<SyncQueueDrainWorker>(
                BackgroundSyncScheduler.MIN_PERIODIC_INTERVAL.inWholeMinutes,
                TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                ).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )

        val immediate =
            OneTimeWorkRequestBuilder<SyncQueueDrainWorker>()
                .setConstraints(constraints)
                .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediate,
        )
    }

    override fun cancelPeriodicSync() {
        WorkManager.getInstance(appContext).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
