package com.aniko.data.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Как часто опрашиваются локальные сетевые интерфейсы, см. KDoc [DesktopConnectivityMonitor]. */
private val DEFAULT_POLL_INTERVAL = 10.seconds

/**
 * Desktop-реализация [ConnectivityMonitor] (P10.T2).
 *
 * **Принятое решение и почему именно оно.** У JVM нет никакого события «сеть появилась/пропала» —
 * аналога `ConnectivityManager.NetworkCallback` или `NWPathMonitor` в стандартной библиотеке
 * просто не существует, поэтому любой вариант здесь — компромисс. Из трёх рассмотренных выбран
 * третий:
 *
 * 1. Периодический DNS-резолв или TCP-коннект к `api-s.anixsekai.com` — отвергнут: это регулярный
 *    сетевой трафик к чужому серверу только ради индикатора в UI, причём круглосуточно, пока
 *    открыто окно приложения.
 * 2. Выводить статус только из результатов обычных запросов приложения — отвергнут как
 *    единственный механизм: пока пользователь ничего не делает, статус «залипает» на последнем
 *    известном, и восстановление связи не будет замечено вообще (а именно оно и должно
 *    триггерить дренаж очереди, P10.T2).
 * 3. **Выбрано:** опрос локальных сетевых интерфейсов ОС (`NetworkInterface.getNetworkInterfaces`)
 *    раз в [pollInterval]. Это чисто локальный вызов к ядру, наружу не уходит ни одного пакета,
 *    поэтому «не спамить сеть» соблюдено буквально.
 *
 * **Что этот способ не ловит (осознанное ограничение, не баг):** «интерфейс поднят, но интернета
 * нет» — captive portal в отеле, упавший роутер, DNS-дыра. В этих случаях монитор скажет
 * [ConnectivityStatus.Online], дренаж запустится и получит сетевую ошибку — но это ровно та
 * ситуация, на которую уже рассчитан экспоненциальный backoff [SyncQueueWorker] (P4.T6), так что
 * последствие — одна лишняя неудачная попытка, а не поломка. Ловится же главный практический
 * случай: Wi-Fi выключен / кабель выдернут / ноутбук вышел из сна без сети.
 *
 * @param pollInterval интервал опроса; параметр (а не константа) ради тестируемости.
 * @param dispatcher диспетчер, на котором делается блокирующий вызов `NetworkInterface`.
 */
class DesktopConnectivityMonitor(
    private val dispatcher: CoroutineDispatcher,
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
) : ConnectivityMonitor {
    override val status: Flow<ConnectivityStatus> =
        flow {
            while (true) {
                emit(currentStatus())
                delay(pollInterval)
            }
        }.distinctUntilChanged().flowOn(dispatcher)

    // ОС отказалась перечислять интерфейсы — не знаем ничего, а не «оффлайн»: ложный баннер
    // хуже отсутствия баннера (см. KDoc ConnectivityStatus.Unknown). Само исключение никуда не
    // логируется намеренно — у модуля нет логгера, а на пользовательское поведение оно не влияет.
    @Suppress("SwallowedException")
    private fun currentStatus(): ConnectivityStatus =
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.asSequence().orEmpty()
            if (interfaces.any(::isUsable)) ConnectivityStatus.Online else ConnectivityStatus.Offline
        } catch (error: SocketException) {
            ConnectivityStatus.Unknown
        }
}

/**
 * Интерфейс годится как признак «сеть есть», если он поднят, не loopback и имеет хотя бы один
 * адрес вне link-local (`169.254.x.x` / `fe80::` — их ОС назначает сама при ОТСУТСТВИИ реальной
 * конфигурации, так что сами по себе они онлайна не означают).
 */
private fun isUsable(networkInterface: NetworkInterface): Boolean =
    networkInterface.isUp &&
        !networkInterface.isLoopback &&
        networkInterface.inetAddresses.asSequence().any { !it.isLinkLocalAddress }
