package com.aniko.app.smoke

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aniko.data.notification.LocalNotification
import com.aniko.data.notification.LocalNotificationPresenter
import com.aniko.data.notification.NotificationContentFactory
import com.aniko.data.session.SecureTokenStorage
import com.aniko.data.sync.BackgroundSyncScheduler
import com.aniko.data.sync.ConnectivityMonitor
import com.aniko.data.sync.ConnectivityStatus
import com.aniko.database.AnikoDatabase
import com.aniko.database.driver.DatabaseDriverFactory
import com.aniko.network.AnixJson
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.dsl.module

/**
 * In-memory [SecureTokenStorage] для тестов — не трогает системный Keychain/Keystore процесса, на
 * котором гоняются тесты (в отличие от `MacKeychainTokenStorage`, которую use'ет реальный
 * `platformModule()` на Desktop, см. `composeApp/src/desktopMain/.../PlatformModule.desktop.kt`).
 */
private class InMemorySecureTokenStorage(
    initialToken: String?,
) : SecureTokenStorage {
    private var token: String? = initialToken

    override suspend fun get(): String? = token

    override suspend fun set(token: String) {
        this.token = token
    }

    override suspend fun clear() {
        token = null
    }
}

/** Периодическую фоновую синхронизацию в смоук-тестах включать не нужно — оба метода no-op. */
private object NoOpBackgroundSyncScheduler : BackgroundSyncScheduler {
    override fun schedulePeriodicSync() = Unit

    override fun cancelPeriodicSync() = Unit
}

/**
 * Сеть в смоук-тестах фейковая (см. [fakeApiEngine]), но всегда «доступна» — иначе
 * `SyncCoordinator` держал бы UI на [ConnectivityStatus.Unknown]/офлайн-баннере поверх каждого
 * экрана.
 */
private object AlwaysOnlineConnectivityMonitor : ConnectivityMonitor {
    override val status = MutableStateFlow(ConnectivityStatus.Online)
}

/** Системного трея/центра уведомлений в тестовом процессе нет и не нужно. */
private object NoOpLocalNotificationPresenter : LocalNotificationPresenter {
    override suspend fun ensurePermission(): Boolean = false

    override suspend fun show(notification: LocalNotification) = Unit
}

/** In-memory [DatabaseDriverFactory] — своя SQLite-БД на каждый запуск [fakeInfraModule], без файлов на диске. */
private fun inMemoryDatabaseDriverFactory(): DatabaseDriverFactory =
    object : DatabaseDriverFactory {
        override fun create(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AnikoDatabase.Schema.create(it) }
    }

/**
 * Полная замена продовой связки `platformModule() + HttpClient` (F3, Фаза 11) для смоук-тестов
 * composeApp: никакой реальной сети, файловой БД или системного Keychain — см. KDoc отдельных
 * fake-реализаций выше.
 *
 * ВАЖНО про порядок: регистрировать ПОСЛЕДНИМ в списке `modules(...)` при вызове `startKoin`
 * (см. [runAnikoSmokeTest]) — Koin отдаёт приоритет последнему объявлению `single` для одного и
 * того же типа, поэтому этот модуль обязан идти позже `com.aniko.database.di.databaseModule` и
 * `com.aniko.data.di.dataModule`, иначе он не переопределит их `HttpClient`/`DatabaseDriverFactory`.
 *
 * @param apiRoutes маршруты для [fakeApiEngine] сверх [defaultFixtureRoutes].
 * @param initialToken если не `null`, `SessionStore.bootstrap()` (вызывается из `App()` на
 * старте) увидит пользователя уже авторизованным — иначе стартовый экран приложения — логин.
 */
fun fakeInfraModule(
    apiRoutes: Map<String, () -> String> = emptyMap(),
    initialToken: String? = null,
) = module {
    single<Settings> { MapSettings() }
    single<SecureTokenStorage> { InMemorySecureTokenStorage(initialToken) }
    single<DatabaseDriverFactory> { inMemoryDatabaseDriverFactory() }
    single<ConnectivityMonitor> { AlwaysOnlineConnectivityMonitor }
    single<BackgroundSyncScheduler> { NoOpBackgroundSyncScheduler }
    single<LocalNotificationPresenter> { NoOpLocalNotificationPresenter }
    // `null` — ни одно локальное уведомление не будет реально показано (см. NotificationPoller).
    single<NotificationContentFactory> { NotificationContentFactory { null } }

    single<HttpClient> {
        HttpClient(fakeApiEngine(apiRoutes)) {
            expectSuccess = true
            install(ContentNegotiation) { json(AnixJson) }
        }
    }
}
