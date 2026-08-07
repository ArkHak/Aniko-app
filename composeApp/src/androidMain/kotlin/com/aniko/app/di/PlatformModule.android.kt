package com.aniko.app.di

import android.content.Context
import com.aniko.data.session.AndroidSecureTokenStorage
import com.aniko.data.session.SecureTokenStorage
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<Settings> {
            SharedPreferencesSettings(
                androidContext().getSharedPreferences("aniko.prefs", Context.MODE_PRIVATE),
            )
        }
        single<SecureTokenStorage> { AndroidSecureTokenStorage(androidContext()) }
    }
