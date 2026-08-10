package com.aniko.app.di

import com.aniko.data.session.MacKeychainTokenStorage
import com.aniko.data.session.SecureTokenStorage
import com.aniko.database.driver.DatabaseDriverFactory
import com.aniko.database.driver.DesktopDatabaseDriverFactory
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

actual fun platformModule(): Module =
    module {
        single<Settings> {
            PreferencesSettings(Preferences.userRoot().node("com/aniko/app"))
        }
        single<SecureTokenStorage> { MacKeychainTokenStorage() }
        single<DatabaseDriverFactory> { DesktopDatabaseDriverFactory() }
    }
