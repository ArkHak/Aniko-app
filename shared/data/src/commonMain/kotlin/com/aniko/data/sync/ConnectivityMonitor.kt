package com.aniko.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * Наблюдение за подключением к сети (P10.T2).
 *
 * Почему обычный интерфейс с платформенными реализациями, а не `expect class`: Android-реализации
 * нужен `Context`, которого нет в `commonMain` и который нельзя протащить через `expect class
 * ConnectivityMonitor()` без глобального холдера контекста. Ровно та же причина и ровно тот же
 * приём уже приняты в проекте для `DatabaseDriverFactory` (`:shared:database`) и
 * `SecureTokenStorage` — реализации лежат в `androidMain`/`iosMain`/`desktopMain` этого же модуля,
 * а биндинг делает `platformModule()` в `composeApp`, где `androidContext()` доступен.
 *
 * Реализации:
 * - `AndroidConnectivityMonitor` — `ConnectivityManager.registerDefaultNetworkCallback`;
 * - `IosConnectivityMonitor` — `NWPathMonitor` (Network.framework);
 * - `DesktopConnectivityMonitor` — периодический опрос локальных сетевых интерфейсов JVM,
 *   см. KDoc самой реализации про то, почему именно так, а не DNS/TCP-пинг.
 */
interface ConnectivityMonitor {
    /**
     * Горячий поток статуса. Первое значение реализация обязана отдать сразу при подписке (пусть
     * даже [ConnectivityStatus.Unknown]), чтобы подписчик не висел без состояния. Дубликаты
     * подряд идущих одинаковых значений реализация отсеивает сама (`distinctUntilChanged`).
     */
    val status: Flow<ConnectivityStatus>
}
