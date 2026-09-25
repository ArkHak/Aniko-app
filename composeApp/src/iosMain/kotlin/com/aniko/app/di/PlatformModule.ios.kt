package com.aniko.app.di

import com.aniko.data.di.APP_SCOPE
import com.aniko.data.notification.IosLocalNotificationPresenter
import com.aniko.data.notification.LocalNotificationPresenter
import com.aniko.data.session.IosKeychainTokenStorage
import com.aniko.data.session.SecureTokenStorage
import com.aniko.data.sync.BackgroundSyncScheduler
import com.aniko.data.sync.ConnectivityMonitor
import com.aniko.data.sync.IosBackgroundSyncScheduler
import com.aniko.data.sync.IosConnectivityMonitor
import com.aniko.data.update.AppUpdateInstaller
import com.aniko.data.update.OpenPageOnlyInstaller
import com.aniko.data.update.UpdatePlatform
import com.aniko.database.driver.DatabaseDriverFactory
import com.aniko.database.driver.IosDatabaseDriverFactory
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults

actual fun platformModule(): Module =
    module {
        single<Settings> {
            NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
        }
        single<SecureTokenStorage> { IosKeychainTokenStorage() }
        single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }
        // P10.T1/T2 — платформенные реализации на Network.framework и BGTaskScheduler.
        single<ConnectivityMonitor> { IosConnectivityMonitor() }
        single<BackgroundSyncScheduler> {
            IosBackgroundSyncScheduler(task = get(), scope = get(named(APP_SCOPE)))
        }
        // P10.T6 — локальные уведомления через UNUserNotificationCenter.
        single<LocalNotificationPresenter> { IosLocalNotificationPresenter() }
        // P16.T21 — iOS не поддерживает несколько иконок лаунчера в этой реализации; секция в
        // SettingsScreen скрывается по пустому `supportedIcons`.
        single<AppIconHelper> { NoOpAppIconHelper() }
        // iOS не умеет обновлять неподписанную side-load сборку из приложения: только страница релиза.
        single<AppUpdateInstaller> { OpenPageOnlyInstaller(UpdatePlatform.Ios, NSTemporaryDirectory().toPath()) }
    }
