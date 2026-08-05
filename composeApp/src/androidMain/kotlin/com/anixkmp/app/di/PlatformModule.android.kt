package com.anixkmp.app.di

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("anixkmp.prefs", Context.MODE_PRIVATE),
        )
    }
}
