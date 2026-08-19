package com.aniko.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Desktop-реализация [BackgroundSyncScheduler] (P10.T1) — корутинный `delay`-цикл
 * ([runPeriodicSyncLoop]) в переданном scope. С P10.T6 в тик входит ещё и опрос уведомлений, но
 * цикл об этом не знает: всё содержимое одного срабатывания живёт в [PeriodicSyncTask].
 *
 * Прямое следствие «фон = пока приложение запущено» именно для уведомлений: на Desktop локальное
 * уведомление о новой серии придёт только при открытом приложении. Системного способа разбудить
 * JVM-процесс по расписанию у нас нет (см. ниже), и обещать иное было бы нечестно.
 *
 * Почему не «настоящий» системный планировщик: у JVM-desktop-приложения его нет. `launchd`
 * (macOS — единственный desktop-таргет проекта, см. `compose.desktop.nativeDistributions`)
 * умел бы будить агент по расписанию, но для этого нужен установленный `.plist` в
 * `~/Library/LaunchAgents` и отдельный запускаемый процесс — это уже не «фоновая синхронизация
 * приложения», а системная служба, что выходит далеко за рамки P10.T1 и требует прав на запись в
 * пользовательский LaunchAgents. Поэтому «фон» на Desktop честно означает «пока приложение
 * запущено» — и это же значит, что офлайн-очередь на Desktop всё равно дренируется при следующем
 * запуске (переход [ConnectivityStatus.Unknown] → [ConnectivityStatus.Online] в
 * [SyncCoordinator]).
 *
 * @param scope тот же долгоживущий scope уровня приложения, что и у [SyncCoordinator] — цикл
 * умирает вместе с процессом.
 */
class DesktopBackgroundSyncScheduler(
    private val task: PeriodicSyncTask,
    private val scope: CoroutineScope,
) : BackgroundSyncScheduler {
    private var job: Job? = null

    override fun schedulePeriodicSync() {
        if (job?.isActive == true) return
        job = scope.launch { runPeriodicSyncLoop(task) }
    }

    override fun cancelPeriodicSync() {
        job?.cancel()
        job = null
    }
}
