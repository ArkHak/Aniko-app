package com.anixkmp.app.di

import com.anixkmp.data.session.IosKeychainTokenStorage
import com.anixkmp.data.session.SecureTokenStorage
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual fun platformModule(): Module = module {
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }
    single<SecureTokenStorage> { IosKeychainTokenStorage() }
}
