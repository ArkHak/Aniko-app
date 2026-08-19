package com.aniko.app.di

import android.content.Context
import com.aniko.data.locale.LocaleStore
import com.aniko.data.notification.AndroidLocalNotificationPresenter
import com.aniko.data.notification.LocalNotificationPresenter
import com.aniko.data.session.AndroidSecureTokenStorage
import com.aniko.data.session.SecureTokenStorage
import com.aniko.data.sync.AndroidBackgroundSyncScheduler
import com.aniko.data.sync.AndroidConnectivityMonitor
import com.aniko.data.sync.BackgroundSyncScheduler
import com.aniko.data.sync.ConnectivityMonitor
import com.aniko.database.driver.AndroidDatabaseDriverFactory
import com.aniko.database.driver.DatabaseDriverFactory
import com.aniko.ui.i18n.appStringsFor
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
        single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
        // P10.T1/T2 — обеим реализациям нужен Context, поэтому они здесь, а не в `dataModule`
        // (та же причина, что у Settings/SecureTokenStorage/DatabaseDriverFactory выше).
        single<ConnectivityMonitor> { AndroidConnectivityMonitor(androidContext()) }
        single<BackgroundSyncScheduler> { AndroidBackgroundSyncScheduler(androidContext()) }
        // P10.T6. Имя канала видно пользователю в системных настройках, поэтому оно локализовано —
        // но резолвится вне композиции (`appStringsFor`, а не `LocalStrings`): канал создаётся из
        // фонового воркера, где никакой композиции нет. Смена языка в приложении подхватится при
        // следующем показе — `createNotificationChannel` обновляет имя уже созданного канала.
        single<LocalNotificationPresenter> {
            AndroidLocalNotificationPresenter(
                context = androidContext(),
                channelName = appStringsFor(get<LocaleStore>().languageTag.value).notificationChannelName,
            )
        }
    }
