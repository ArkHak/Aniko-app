package com.aniko.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Android-реализация [ConnectivityMonitor] на `ConnectivityManager.registerDefaultNetworkCallback`
 * (P10.T2).
 *
 * Смотрим именно на DEFAULT-сеть, а не на произвольный набор сетей: приложению важно ровно то,
 * куда система маршрутизирует его исходящий трафик. У default-сети в каждый момент времени
 * ровно один экземпляр, поэтому здесь не нужен учёт множества `Network` — достаточно
 * `onAvailable`/`onLost`.
 *
 * `NET_CAPABILITY_VALIDATED` проверяется дополнительно к `NET_CAPABILITY_INTERNET`: без него
 * подключение к Wi-Fi с captive portal («залогиньтесь в отеле») считалось бы онлайном, и мы бы
 * зря дренировали очередь в стену. Требуется `ACCESS_NETWORK_STATE` — разрешение уже объявлено в
 * `composeApp/src/androidMain/AndroidManifest.xml`.
 */
class AndroidConnectivityMonitor(
    context: Context,
) : ConnectivityMonitor {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override val status: Flow<ConnectivityStatus> =
        callbackFlow {
            val manager = connectivityManager
            if (manager == null) {
                // Сервис недоступен (нестандартная прошивка/тестовый контекст) — не притворяемся,
                // что знаем состояние: отдаём Unknown, UI не покажет ложный баннер офлайна.
                trySend(ConnectivityStatus.Unknown)
                awaitClose { }
                return@callbackFlow
            }

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(manager.statusOf(network))
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        trySend(capabilities.toStatus())
                    }

                    override fun onLost(network: Network) {
                        trySend(ConnectivityStatus.Offline)
                    }

                    override fun onUnavailable() {
                        trySend(ConnectivityStatus.Offline)
                    }
                }

            trySend(manager.currentStatus())
            manager.registerDefaultNetworkCallback(callback)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()
}

private fun NetworkCapabilities?.toStatus(): ConnectivityStatus =
    if (this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    ) {
        ConnectivityStatus.Online
    } else {
        ConnectivityStatus.Offline
    }

private fun ConnectivityManager.statusOf(net: Network): ConnectivityStatus = getNetworkCapabilities(net).toStatus()

// Блочное тело, а не выражение: в одну строку (ktlint схлопывает выражения, которые в неё
// помещаются) оно перестаёт помещаться в лимит длины строки detekt.
private fun ConnectivityManager.currentStatus(): ConnectivityStatus {
    val network = activeNetwork ?: return ConnectivityStatus.Offline
    return statusOf(network)
}
