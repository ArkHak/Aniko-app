package com.aniko.app.di

import com.aniko.app.buildinfo.BuildInfo
import com.aniko.app.feature.auth.LoginViewModel
import com.aniko.app.feature.auth.RegisterViewModel
import com.aniko.app.feature.collections.CollectionsViewModel
import com.aniko.app.feature.comments.CommentsViewModel
import com.aniko.app.feature.feed.FeedViewModel
import com.aniko.app.feature.gallery.TokenGalleryViewModel
import com.aniko.app.feature.home.HomeViewModel
import com.aniko.app.feature.library.LibraryViewModel
import com.aniko.app.feature.notifications.NotificationsViewModel
import com.aniko.app.feature.player.PlayerViewModel
import com.aniko.app.feature.profile.ProfileViewModel
import com.aniko.app.feature.release.ReleaseDetailsViewModel
import com.aniko.app.feature.release.rating.ReleaseRatingViewModel
import com.aniko.app.feature.schedule.ScheduleViewModel
import com.aniko.app.feature.search.SearchViewModel
import com.aniko.app.feature.settings.NotificationSettingsViewModel
import com.aniko.app.feature.settings.SettingsViewModel
import com.aniko.app.notification.AppNotificationContentFactory
import com.aniko.data.di.APP_SCOPE
import com.aniko.data.notification.NotificationContentFactory
import com.aniko.data.update.AppVersion
import com.aniko.data.update.GitHubReleaseSource
import com.aniko.data.update.UpdateChecker
import com.aniko.data.update.UpdateCoordinator
import com.aniko.data.update.UpdateDownloader
import com.aniko.data.update.UpdateStore
import com.aniko.network.ApiConfig
import com.aniko.network.createUpdateHttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * DI-граф фичевого слоя.
 *
 * Фичи живут пакетами внутри `composeApp` (не отдельными Gradle-модулями) — так решено
 * в плане ради скорости соло-разработки. Когда модуль перерастёт себя,
 * пакет `feature/<name>` вынимается в отдельный Gradle-модуль почти без правок.
 */
val appModule =
    module {
        // Предрелизный аудит безопасности (#109): HTTP-логи (полные URL и заголовки запросов) только
        // в debug-сборке. `ApiConfig` поставляет приложение, а не `dataModule`, потому что тип
        // сборки известен только здесь (`isDebugBuild()` — expect/actual, без проверок платформы).
        single { ApiConfig(enableLogging = isDebugBuild()) }
        // Найдено вживую на iOS/Desktop (сверка с макетом, 2026-08-23): без этого биндинга
        // приложение падало на КАЖДОМ старте с NoDefinitionFoundException — NotificationPoller
        // (shared/data) требует NotificationContentFactory, а единственная реализация
        // (AppNotificationContentFactory) нигде не регистрировалась в Koin. На Android это не
        // проявлялось, потому что BackgroundSyncScheduler.android.kt не резолвит цепочку жадно
        // при старте — iOS/Desktop-версии резолвят.
        single<NotificationContentFactory> { AppNotificationContentFactory(localeStore = get()) }
        // Автообновление (GitHub Releases публичного репозитория). Свой HTTP-клиент без токена Anixart;
        // `AppUpdateInstaller` регистрирует платформенный модуль.
        single(named(UPDATE_HTTP_CLIENT)) { createUpdateHttpClient() }
        single { UpdateStore(settings = get()) }
        single {
            UpdateChecker(
                source = GitHubReleaseSource(client = get(named(UPDATE_HTTP_CLIENT))),
                store = get(),
                currentVersion = AppVersion.parse(BuildInfo.APP_VERSION) ?: AppVersion(0, 0, 0),
                nowMs = { Clock.System.now().toEpochMilliseconds() },
            )
        }
        single { UpdateDownloader(client = get(named(UPDATE_HTTP_CLIENT))) }
        single {
            UpdateCoordinator(
                checker = get(),
                downloader = get(),
                installer = get(),
                scope = get(named(APP_SCOPE)),
            )
        }
        viewModelOf(::HomeViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::RegisterViewModel)
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
        viewModelOf(::NotificationsViewModel)
        viewModelOf(::FeedViewModel)
        viewModelOf(::CollectionsViewModel)
    }

/** Квалификатор HTTP-клиента обновлений: без токена Anixart и без Anixart-плагинов. */
internal const val UPDATE_HTTP_CLIENT = "update_http_client"
