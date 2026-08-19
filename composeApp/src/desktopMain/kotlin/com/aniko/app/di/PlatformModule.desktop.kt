package com.aniko.app.di

import com.aniko.data.di.APP_SCOPE
import com.aniko.data.notification.DesktopLocalNotificationPresenter
import com.aniko.data.notification.LocalNotificationPresenter
import com.aniko.data.session.MacKeychainTokenStorage
import com.aniko.data.session.SecureTokenStorage
import com.aniko.data.sync.BackgroundSyncScheduler
import com.aniko.data.sync.ConnectivityMonitor
import com.aniko.data.sync.DesktopBackgroundSyncScheduler
import com.aniko.data.sync.DesktopConnectivityMonitor
import com.aniko.database.driver.DatabaseDriverFactory
import com.aniko.database.driver.DesktopDatabaseDriverFactory
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.prefs.Preferences

actual fun platformModule(): Module =
    module {
        single<Settings> {
            PreferencesSettings(Preferences.userRoot().node("com/aniko/app"))
        }
        single<SecureTokenStorage> { MacKeychainTokenStorage() }
        single<DatabaseDriverFactory> { DesktopDatabaseDriverFactory() }
        // P10.T1/T2 — на JVM нет ни системного планировщика фоновых задач, ни событий о смене
        // состояния сети, поэтому обе реализации живут внутри процесса (см. их KDoc).
        single<ConnectivityMonitor> { DesktopConnectivityMonitor(dispatcher = get(named("io"))) }
        single<BackgroundSyncScheduler> {
            DesktopBackgroundSyncScheduler(task = get(), scope = get(named(APP_SCOPE)))
        }
        // P10.T5 — локальные уведомления на Desktop через системный трей (java.awt.SystemTray).
        single<LocalNotificationPresenter> { DesktopLocalNotificationPresenter() }
    }
