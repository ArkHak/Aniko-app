package com.aniko.app.di

import com.aniko.data.session.IosKeychainTokenStorage
import com.aniko.data.session.SecureTokenStorage
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual fun platformModule(): Module =
    module {
        single<Settings> {
            NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
        }
        single<SecureTokenStorage> { IosKeychainTokenStorage() }
    }
