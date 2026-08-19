package com.aniko.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Единая точка склейки Фазы 10 (P10.T1 + P10.T2 + источник состояния для P10.T3).
 *
 * Держит три вещи вместе и больше ничего не делает сам:
 * 1. включает периодический фоновый триггер ([BackgroundSyncScheduler], P10.T1);
 * 2. слушает [ConnectivityMonitor] и на переходе «не-online → online» немедленно дренирует
 *    офлайн-очередь (P10.T2) — это отзывчивый путь, который не заставляет ждать до 15 минут
 *    следующего срабатывания периодической задачи;
 * 3. отдаёт наружу [connectivity] — единственный источник правды о сетевом статусе для UI
 *    (баннер офлайна, P10.T3).
 *
 * Про «не-online → online»: самое первое значение монитора приходит после
 * [ConnectivityStatus.Unknown], поэтому старт приложения при живой сети сам по себе даёт переход
 * `Unknown → Online` и, соответственно, стартовый дренаж очереди. Именно поэтому отдельный
 * `syncQueueWorker.drain()` в `App.kt` (P4.T7) при подключении координатора убран — он стал
 * дублирующим, а не потому что перестал быть нужен.
 *
 * @param scope долгоживущий scope уровня приложения. Передаётся снаружи (DI), а не создаётся
 * внутри: так тест может подставить `TestScope` с виртуальным временем, а приложение — scope,
 * привязанный к процессу.
 */
class SyncCoordinator(
    private val worker: SyncQueueWorker,
    private val connectivityMonitor: ConnectivityMonitor,
    private val scheduler: BackgroundSyncScheduler,
    private val scope: CoroutineScope,
) {
    private val mutableConnectivity = MutableStateFlow(ConnectivityStatus.Unknown)

    /** Текущий сетевой статус для UI (P10.T3). До первого ответа платформы — [ConnectivityStatus.Unknown]. */
    val connectivity: StateFlow<ConnectivityStatus> = mutableConnectivity.asStateFlow()

    private var started = false

    /**
     * Идемпотентный запуск. Вызывается один раз из корневого composable (`App.kt`); повторные
     * вызовы (рекомпозиция, пересоздание `MainViewController` на iOS) — no-op, иначе на каждый
     * вызов заводился бы лишний коллектор connectivity.
     */
    fun start() {
        if (started) return
        started = true
        scheduler.schedulePeriodicSync()
        scope.launch { observeConnectivity() }
    }

    private suspend fun observeConnectivity() {
        connectivityMonitor.status.collect { status ->
            val previous = mutableConnectivity.value
            mutableConnectivity.value = status
            if (status == ConnectivityStatus.Online && previous != ConnectivityStatus.Online) {
                worker.drainSafely()
            }
        }
    }
}
