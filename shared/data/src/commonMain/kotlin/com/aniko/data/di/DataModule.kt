package com.aniko.data.di

import com.aniko.data.api.AuthApi
import com.aniko.data.api.CollectionApi
import com.aniko.data.api.EpisodeApi
import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.FeedApi
import com.aniko.data.api.FilterApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.NotificationApi
import com.aniko.data.api.NotificationPreferenceApi
import com.aniko.data.api.ProfileApi
import com.aniko.data.api.ProfileListApi
import com.aniko.data.api.ProfilePreferenceApi
import com.aniko.data.api.ReleaseApi
import com.aniko.data.api.ReleaseCommentApi
import com.aniko.data.api.ScheduleApi
import com.aniko.data.api.SearchApi
import com.aniko.data.catalogfilter.LocalCatalogFilterStore
import com.aniko.data.locale.LocaleStore
import com.aniko.data.notification.NotificationPoller
import com.aniko.data.notification.NotificationSyncStore
import com.aniko.data.playerposition.LocalPlayerPositionStore
import com.aniko.data.profileshowcase.LocalProfilePinnedSectionStore
import com.aniko.data.repository.AuthRepository
import com.aniko.data.repository.CollectionRepository
import com.aniko.data.repository.CommentRepository
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.FeedRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.data.repository.NotificationPreferenceRepository
import com.aniko.data.repository.NotificationRepository
import com.aniko.data.repository.ProfileRepository
import com.aniko.data.repository.ReleaseRepository
import com.aniko.data.repository.ScheduleRepository
import com.aniko.data.session.SessionStore
import com.aniko.data.sync.PeriodicSyncTask
import com.aniko.data.sync.SyncCoordinator
import com.aniko.data.sync.SyncQueueWorker
import com.aniko.data.theme.AppIconStore
import com.aniko.data.theme.ThemeStore
import com.aniko.data.voicepin.LocalVoicePinStore
import com.aniko.network.ApiConfig
import com.aniko.network.SessionInvalidator
import com.aniko.network.TokenProvider
import com.aniko.network.createAnixHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Квалификатор долгоживущего [CoroutineScope] уровня приложения (P10.T1/T2).
 *
 * Публичный, потому что платформенные реализации [com.aniko.data.sync.BackgroundSyncScheduler]
 * (Desktop, iOS) резолвятся в `composeApp` через `platformModule()` и им нужен тот же самый scope,
 * что и [SyncCoordinator].
 */
const val APP_SCOPE: String = "appScope"

/**
 * Koin-модуль data-слоя.
 *
 * `Settings` сюда НЕ входит: его реализация платформенная (Android требует Context),
 * поэтому его поставляет `composeApp` через свой платформенный модуль. По той же причине снаружи
 * приходят `ConnectivityMonitor` и `BackgroundSyncScheduler` (P10.T1/T2) — здесь собирается только
 * общий [SyncCoordinator] поверх них.
 */
val dataModule =
    module {
        single<Clock> { Clock.System }
        single<CoroutineDispatcher>(named("io")) { ioDispatcher }

        // Scope уровня процесса (P10.T1/T2): здесь живут коллектор connectivity в [SyncCoordinator]
        // и desktop-цикл периодической синхронизации. Именно `single`, а не создание на месте
        // использования — оба потребителя обязаны делить один scope, иначе их нельзя остановить
        // одним движением. [SupervisorJob] — чтобы падение одной дочерней корутины не уносило
        // остальные. Отменять его некому и незачем: он живёт ровно столько же, сколько процесс.
        single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(named("io"))) }

        single { ApiConfig() }

        single { SessionStore(settings = get(), secureStorage = get()) }
        single<TokenProvider> { get<SessionStore>() }
        single<SessionInvalidator> { get<SessionStore>() }
        single { LocaleStore(settings = get()) }
        single { ThemeStore(settings = get()) }
        single { AppIconStore(settings = get()) }
        single { LocalPlayerPositionStore(settings = get()) }
        single { LocalVoicePinStore(settings = get()) }
        single { LocalCatalogFilterStore(settings = get()) }
        single { LocalProfilePinnedSectionStore(settings = get()) }

        single<HttpClient> {
            createAnixHttpClient(
                apiConfig = get(),
                tokenProvider = get(),
                sessionInvalidator = get(),
            )
        }

        single { AuthApi(client = get()) }
        single { ReleaseApi(client = get()) }
        single { EpisodeApi(client = get()) }
        single { ProfileListApi(client = get()) }
        single { FavoriteApi(client = get()) }
        single { HistoryApi(client = get()) }
        single { SearchApi(client = get()) }
        single { ProfileApi(client = get()) }
        single { ProfilePreferenceApi(client = get()) }
        single { ReleaseCommentApi(client = get()) }
        single { ScheduleApi(client = get()) }
        single { FilterApi(client = get()) }
        single { NotificationApi(client = get()) }
        single { NotificationPreferenceApi(client = get()) }
        single { FeedApi(client = get()) }
        single { CollectionApi(client = get()) }

        // P10.T6 — опрос уведомлений. `LocalNotificationPresenter` и `NotificationContentFactory`
        // приходят снаружи: первый платформенный (`platformModule()`), второй живёт в `composeApp`,
        // потому что тексты уведомлений локализованы, а i18n лежит в `shared/ui`, от которого
        // `shared/data` не зависит (см. KDoc `NotificationContentFactory`).
        single { NotificationSyncStore(settings = get()) }
        single {
            NotificationPoller(
                api = get(),
                store = get(),
                presenter = get(),
                contentFactory = get(),
            )
        }
        single { NotificationPreferenceRepository(api = get()) }
        single { NotificationRepository(api = get(), syncStore = get()) }
        single { FeedRepository(api = get()) }
        single { CollectionRepository(api = get()) }

        single {
            SyncQueueWorker(
                queue = get(),
                membership = get(),
                progress = get(),
                profileListApi = get(),
                favoriteApi = get(),
                historyApi = get(),
                episodeApi = get(),
                clock = get(),
            )
        }

        // Содержимое одного срабатывания периодической задачи (P10.T1 + P10.T6). Один на все три
        // платформы — платформенные `BackgroundSyncScheduler` только решают, КОГДА его звать.
        single {
            PeriodicSyncTask(
                sessionStore = get(),
                worker = get(),
                notificationPoller = get(),
            )
        }

        single {
            SyncCoordinator(
                worker = get(),
                connectivityMonitor = get(),
                scheduler = get(),
                scope = get(named(APP_SCOPE)),
            )
        }

        single { AuthRepository(authApi = get(), sessionStore = get()) }
        single {
            ReleaseRepository(
                releaseApi = get(),
                searchApi = get(),
                filterApi = get(),
                releaseCacheStore = get(),
                releaseListStore = get(),
                listMembershipStore = get(),
                clock = get(),
            )
        }
        single { CommentRepository(commentApi = get()) }
        single {
            ScheduleRepository(
                scheduleApi = get(),
                releaseCacheStore = get(),
                releaseListStore = get(),
                listMembershipStore = get(),
                clock = get(),
            )
        }
        single {
            EpisodeRepository(
                episodeApi = get(),
                episodeProgressStore = get(),
                syncQueueStore = get(),
                syncQueueWorker = get(),
                clock = get(),
            )
        }
        single {
            LibraryRepository(
                profileListApi = get(),
                favoriteApi = get(),
                historyApi = get(),
                listMembershipStore = get(),
                releaseCacheStore = get(),
                releaseListStore = get(),
                syncQueueStore = get(),
                syncQueueWorker = get(),
                clock = get(),
            )
        }
        single { ProfileRepository(profileApi = get(), profilePreferenceApi = get(), sessionStore = get()) }
    }
