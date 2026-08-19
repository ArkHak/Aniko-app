package com.aniko.data.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * iOS-реализация [ConnectivityMonitor] на `NWPathMonitor` из Network.framework (P10.T2).
 *
 * В Kotlin/Native доступен C-интерфейс фреймворка (`nw_path_monitor_*`), а не Swift-обёртка
 * `NWPathMonitor` — это тот же самый механизм, просто без сахара: создаём монитор, вешаем
 * update-handler на собственную последовательную очередь и переводим `nw_path_status_satisfied`
 * в [ConnectivityStatus.Online].
 *
 * `satisfied` означает «система считает, что через этот путь можно слать трафик». Состояние
 * `requires_connection` (нужен VPN on-demand) сюда осознанно НЕ приравнивается к онлайну:
 * попытка дренажа в этот момент упёрлась бы в сетевую ошибку.
 *
 * Первое значение приходит от системы асинхронно, поэтому подписчик до него видит
 * [ConnectivityStatus.Unknown], который эмитится сразу при подписке — контракт
 * [ConnectivityMonitor.status].
 */
class IosConnectivityMonitor : ConnectivityMonitor {
    @OptIn(ExperimentalForeignApi::class)
    override val status: Flow<ConnectivityStatus> =
        callbackFlow {
            trySend(ConnectivityStatus.Unknown)

            val monitor = nw_path_monitor_create()
            // attr = null — это и есть последовательная очередь (в C так же определён
            // `DISPATCH_QUEUE_SERIAL`). Сама константа здесь не годится: в Kotlin/Native она
            // приезжает как `CPointer`, а `dispatch_queue_create` ждёт `dispatch_queue_attr_t?`
            // (= `NSObject?`), типы не сходятся.
            val queue = dispatch_queue_create("com.aniko.connectivity", attr = null)

            nw_path_monitor_set_update_handler(monitor) { path ->
                val online = nw_path_get_status(path) == nw_path_status_satisfied
                trySend(if (online) ConnectivityStatus.Online else ConnectivityStatus.Offline)
            }
            nw_path_monitor_set_queue(monitor, queue)
            nw_path_monitor_start(monitor)

            awaitClose { nw_path_monitor_cancel(monitor) }
        }.distinctUntilChanged()
}
