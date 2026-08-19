package com.aniko.data.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Периодический фоновый триггер [PeriodicSyncTask] (P10.T1 + P10.T6).
 *
 * Изначально дёргал только [SyncQueueWorker.drain]; с приходом опроса уведомлений (P10.T6)
 * содержимое одного срабатывания вынесено в [PeriodicSyncTask] — второй планировщик под
 * уведомления заводить не стали намеренно, см. его KDoc.
 *
 * Зачем он нужен сверх уже существующих вызовов `drain()` (синхронно после каждой мутации в
 * `LibraryRepository`/`EpisodeRepository` и разово на старте приложения): операция, получившая
 * retryable-ошибку, получает `nextAttemptAt` в будущем, но НИКТО не приходит за ней в этот
 * момент — она просто ждёт следующей пользовательской мутации или следующего запуска приложения.
 * Если связи не было дольше, чем пользователь держал приложение открытым, очередь может
 * простаивать сколько угодно долго. Этот планировщик — второй, независимый от действий
 * пользователя источник вызовов; третий, самый отзывчивый — переход offline→online в
 * [SyncCoordinator].
 *
 * Реализации и их системные ограничения (не обходятся, а принимаются как есть):
 * - Android — `WorkManager`. Минимальный период `PeriodicWorkRequest` — 15 минут, это жёсткое
 *   ограничение платформы.
 * - iOS — `BGTaskScheduler`/`BGAppRefreshTask`. [MIN_PERIODIC_INTERVAL] там задаёт только
 *   `earliestBeginDate`; РЕАЛЬНОЕ время запуска выбирает система по своим эвристикам, гарантий
 *   частоты нет вообще, и приложение может не получить фонового времени неделями.
 * - Desktop — обычный корутинный `delay`-цикл, живущий столько же, сколько процесс. Системного
 *   планировщика фоновых задач у JVM-desktop-приложения нет, поэтому «фон» здесь означает
 *   «пока приложение запущено».
 */
interface BackgroundSyncScheduler {
    /**
     * Регистрация платформенных обработчиков фоновых задач.
     *
     * Отдельный шаг, а не часть [schedulePeriodicSync], из-за iOS: `BGTaskScheduler.register` обязан
     * быть вызван ДО конца `application(_:didFinishLaunchingWithOptions:)`, иначе система бросает
     * `NSInternalInconsistencyException`. Composable-дерево (откуда вызывается
     * [SyncCoordinator.start]) строится уже после этого момента, поэтому регистрацию зовёт
     * `AppDelegate` из Swift через `registerBackgroundSyncTasks()`.
     *
     * На Android и Desktop регистрировать нечего — дефолтная реализация no-op.
     */
    fun registerHandlers() = Unit

    /** Ставит/обновляет периодическую задачу. Идемпотентно: повторный вызов не плодит задачи. */
    fun schedulePeriodicSync()

    /** Снимает периодическую задачу (используется при логауте/остановке процесса). */
    fun cancelPeriodicSync()

    companion object {
        /**
         * 15 минут — не наш выбор, а минимальный период `PeriodicWorkRequest` в Android
         * (`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`). Держим одно значение на все
         * платформы, чтобы поведение не расходилось без причины.
         */
        val MIN_PERIODIC_INTERVAL: Duration = 15.minutes

        /**
         * Идентификатор фоновой задачи. На iOS обязан совпадать со значением в
         * `BGTaskSchedulerPermittedIdentifiers` (`iosApp/iosApp/Info.plist`), на Android
         * используется как имя unique-work.
         */
        const val SYNC_TASK_IDENTIFIER: String = "com.aniko.app.sync-queue-drain"
    }
}
