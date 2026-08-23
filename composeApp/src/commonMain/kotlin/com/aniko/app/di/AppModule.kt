package com.aniko.app.di

import com.aniko.app.feature.auth.LoginViewModel
import com.aniko.app.feature.comments.CommentsViewModel
import com.aniko.app.feature.gallery.TokenGalleryViewModel
import com.aniko.app.feature.home.HomeViewModel
import com.aniko.app.feature.library.LibraryViewModel
import com.aniko.app.feature.player.PlayerViewModel
import com.aniko.app.feature.profile.ProfileViewModel
import com.aniko.app.feature.release.ReleaseDetailsViewModel
import com.aniko.app.feature.release.rating.ReleaseRatingViewModel
import com.aniko.app.feature.schedule.ScheduleViewModel
import com.aniko.app.feature.search.SearchViewModel
import com.aniko.app.feature.settings.NotificationSettingsViewModel
import com.aniko.app.feature.settings.SettingsViewModel
import com.aniko.app.notification.AppNotificationContentFactory
import com.aniko.data.notification.NotificationContentFactory
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * DI-граф фичевого слоя.
 *
 * Фичи живут пакетами внутри `composeApp` (не отдельными Gradle-модулями) — так решено
 * в плане ради скорости соло-разработки. Когда модуль перерастёт себя,
 * пакет `feature/<name>` вынимается в отдельный Gradle-модуль почти без правок.
 */
val appModule =
    module {
        // Найдено вживую на iOS-симуляторе (сверка с макетом, 2026-08-23): без этого биндинга
        // приложение падало на КАЖДОМ старте с NoDefinitionFoundException — NotificationPoller
        // (shared/data) требует NotificationContentFactory, а единственная реализация
        // (AppNotificationContentFactory) нигде не регистрировалась в Koin. На Android это не
        // проявлялось, потому что BackgroundSyncScheduler.android.kt не резолвит цепочку жадно
        // при старте — iOS-версия (registerBackgroundSyncTasks() в AppDelegate) резолвит.
        single<NotificationContentFactory> { AppNotificationContentFactory(localeStore = get()) }
        viewModelOf(::HomeViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::SettingsViewModel)
        // Найдено на устройстве (Фаза 11, T9): регистрация в Koin отсутствовала — экран
        // "Настройки → Уведомления" падал с NoDefinitionFoundException при каждом открытии.
        viewModelOf(::NotificationSettingsViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::ReleaseDetailsViewModel)
        viewModelOf(::ReleaseRatingViewModel)
        viewModelOf(::PlayerViewModel)
        viewModelOf(::LibraryViewModel)
        viewModelOf(::ProfileViewModel)
        viewModelOf(::TokenGalleryViewModel)
        viewModelOf(::ScheduleViewModel)
        viewModelOf(::CommentsViewModel)
    }
