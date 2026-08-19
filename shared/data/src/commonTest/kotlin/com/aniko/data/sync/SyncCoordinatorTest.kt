package com.aniko.data.sync

import com.aniko.data.api.EpisodeApi
import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.NotificationApi
import com.aniko.data.api.ProfileListApi
import com.aniko.data.cache.FakeClock
import com.aniko.data.notification.LocalNotification
import com.aniko.data.notification.LocalNotificationPresenter
import com.aniko.data.notification.NotificationContentFactory
import com.aniko.data.notification.NotificationPoller
import com.aniko.data.notification.NotificationSyncStore
import com.aniko.data.session.FakeSecureTokenStorage
import com.aniko.data.session.SessionStore
import com.aniko.database.store.SyncQueueStore
import com.aniko.database.sync.SyncOperation
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Тесты склейки Фазы 10: [SyncCoordinator] (P10.T1/T2) и [runPeriodicSyncLoop] (P10.T1, Desktop).
 *
 * Дренаж считается не по HTTP-вызовам, а по обращениям к [SyncQueueStore.dueOperations]
 * ([DrainCountingQueueStore]): именно с них начинается каждый проход [SyncQueueWorker.drain], и
 * счётчик срабатывает даже когда очередь пуста — а во всех сценариях этого файла она пуста, потому
 * что проверяется не поведение самого воркера (это `SyncQueuePlannerTest`), а то, КОГДА его зовут.
 * `MockEngine` соответственно настроен падать на любом запросе: сетевого трафика здесь быть не
 * должно вообще.
 *
 * **Про [UnconfinedTestDispatcher] вместо `backgroundScope`.** Координатору нужен долгоживущий
 * scope, чей коллектор connectivity не завершается сам — то есть ровно то, для чего в
 * `kotlinx-coroutines-test` существует `backgroundScope`. Он здесь НЕ подходит: с дефолтным
 * `StandardTestDispatcher` подписка на `MutableSharedFlow` в фоновом scope не успевает произойти к
 * моменту `emit()` из тела теста, и первое значение теряется (проверено — тест молча видел ноль
 * дренажей). Unconfined-диспетчер запускает коллектор синхронно прямо на `start()`, что убирает
 * гонку целиком; виртуальное время при этом сохраняется, потому что диспетчер построен на
 * [TestScope.testScheduler] самого теста.
 */
class SyncCoordinatorTest {
    private fun syncWorker(queue: SyncQueueStore): SyncQueueWorker {
        val client = HttpClient(MockEngine { request -> error("Unexpected HTTP call: ${request.url}") })
        return SyncQueueWorker(
            queue = queue,
            membership = FakeListMembershipStore(),
            progress = FakeEpisodeProgressStore(),
            profileListApi = ProfileListApi(client),
            favoriteApi = FavoriteApi(client),
            historyApi = HistoryApi(client),
            episodeApi = EpisodeApi(client),
            clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        )
    }

    private fun TestScope.appScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun coordinator(
        queue: SyncQueueStore,
        statuses: Flow<ConnectivityStatus>,
        scope: CoroutineScope,
        scheduler: BackgroundSyncScheduler = RecordingScheduler(),
    ) = SyncCoordinator(
        worker = syncWorker(queue),
        connectivityMonitor = FakeConnectivityMonitor(statuses),
        scheduler = scheduler,
        scope = scope,
    )

    private fun statusFlow() = MutableSharedFlow<ConnectivityStatus>()

    /**
     * [PeriodicSyncTask] с авторизованной [SessionStore] (иначе [PeriodicSyncTask.runOnce]
     * возвращает [SyncQueueWorker.DrainResult.Unauthorized] и до [SyncQueueWorker.drain] дело не
     * доходит вовсе) и [NotificationPoller], у которого [LocalNotificationPresenter.ensurePermission]
     * всегда `false` — этого достаточно, чтобы `poll()` вышел без единого сетевого вызова, поэтому
     * тот же error-бросающий [MockEngine], что и у [syncWorker], безопасен и здесь.
     */
    private suspend fun periodicSyncTask(queue: SyncQueueStore): PeriodicSyncTask {
        val sessionStore = SessionStore(MapSettings(), FakeSecureTokenStorage())
        sessionStore.save(token = "test-token", profileId = 1L)
        val client = HttpClient(MockEngine { request -> error("Unexpected HTTP call: ${request.url}") })
        val notificationPoller =
            NotificationPoller(
                api = NotificationApi(client),
                store = NotificationSyncStore(MapSettings()),
                presenter = NeverPermittedPresenter(),
                contentFactory = NotificationContentFactory { null },
            )
        return PeriodicSyncTask(sessionStore, syncWorker(queue), notificationPoller)
    }

    @Test
    fun `repeated start plans periodic sync exactly once`() =
        runTest {
            val scope = appScope()
            val scheduler = RecordingScheduler()
            val coordinator = coordinator(DrainCountingQueueStore(), statusFlow(), scope, scheduler)

            coordinator.start()
            coordinator.start()
            coordinator.start()

            assertEquals(1, scheduler.scheduleCalls, "повторный start() не должен плодить задачи")
            scope.cancel()
        }

    @Test
    fun `first transition to online drains the queue`() =
        runTest {
            val scope = appScope()
            val queue = DrainCountingQueueStore()
            val statuses = statusFlow()
            val coordinator = coordinator(queue, statuses, scope)

            coordinator.start()
            assertEquals(0, queue.drains, "до первого ответа платформы дренажа быть не должно")

            // Unknown → Online: этот же переход заменяет собой стартовый drain() из P4.T7.
            statuses.emit(ConnectivityStatus.Online)

            assertEquals(1, queue.drains)
            assertEquals(ConnectivityStatus.Online, coordinator.connectivity.value)
            scope.cancel()
        }

    @Test
    fun `connection restored drains again while going offline does not`() =
        runTest {
            val scope = appScope()
            val queue = DrainCountingQueueStore()
            val statuses = statusFlow()
            val coordinator = coordinator(queue, statuses, scope)

            coordinator.start()
            statuses.emit(ConnectivityStatus.Online)

            statuses.emit(ConnectivityStatus.Offline)
            assertEquals(1, queue.drains, "уход в офлайн сам по себе дренаж не запускает")
            assertEquals(ConnectivityStatus.Offline, coordinator.connectivity.value)

            statuses.emit(ConnectivityStatus.Online)
            assertEquals(2, queue.drains, "восстановление связи обязано дренировать очередь (P10.T2)")
            scope.cancel()
        }

    @Test
    fun `unknown status is never reported as offline`() =
        runTest {
            val scope = appScope()
            val queue = DrainCountingQueueStore()
            val statuses = statusFlow()
            val coordinator = coordinator(queue, statuses, scope)

            coordinator.start()
            statuses.emit(ConnectivityStatus.Unknown)

            // Контракт P10.T3: пока платформа не ответила, баннер офлайна не показывается.
            assertFalse(coordinator.connectivity.value.isOffline)
            assertEquals(0, queue.drains)
            scope.cancel()
        }

    @Test
    fun `periodic loop drains once per interval and not before the first one`() =
        runTest {
            val scope = appScope()
            val queue = DrainCountingQueueStore()
            val task = periodicSyncTask(queue)
            val interval = BackgroundSyncScheduler.MIN_PERIODIC_INTERVAL

            scope.launch { runPeriodicSyncLoop(task, interval) }

            advanceTimeBy(interval)
            assertEquals(0, queue.drains, "первый проход — после интервала, а не сразу")

            advanceTimeBy(1.milliseconds)
            assertEquals(1, queue.drains)

            advanceTimeBy(interval)
            assertEquals(2, queue.drains)
            scope.cancel()
        }
}

/** [ConnectivityMonitor] поверх управляемого тестом потока. */
private class FakeConnectivityMonitor(
    override val status: Flow<ConnectivityStatus>,
) : ConnectivityMonitor

/** [LocalNotificationPresenter], который никогда не разрешает показ уведомлений (тесты дренажа). */
private class NeverPermittedPresenter : LocalNotificationPresenter {
    override suspend fun ensurePermission(): Boolean = false

    override suspend fun show(notification: LocalNotification) = error("not reachable: permission denied")
}

/** Считает вызовы [BackgroundSyncScheduler.schedulePeriodicSync], сам ничего не планирует. */
private class RecordingScheduler : BackgroundSyncScheduler {
    var scheduleCalls = 0
        private set

    override fun schedulePeriodicSync() {
        scheduleCalls++
    }

    override fun cancelPeriodicSync() = Unit
}

/**
 * [FakeSyncQueueStore] + счётчик проходов: каждый [SyncQueueWorker.drain] начинается ровно с одного
 * вызова [SyncQueueStore.dueOperations], поэтому его достаточно, чтобы посчитать дренажи, не
 * подменяя сам воркер (он — класс, а не интерфейс, и подменять его пришлось бы наследованием).
 */
private class DrainCountingQueueStore(
    private val delegate: FakeSyncQueueStore = FakeSyncQueueStore(),
) : SyncQueueStore by delegate {
    var drains = 0
        private set

    override suspend fun dueOperations(now: Instant): List<SyncOperation> {
        drains++
        return delegate.dueOperations(now)
    }
}
