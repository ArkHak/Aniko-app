package com.aniko.data.di

import com.aniko.data.api.AuthApi
import com.aniko.data.api.EpisodeApi
import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.FilterApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.ProfileApi
import com.aniko.data.api.ProfileListApi
import com.aniko.data.api.ProfilePreferenceApi
import com.aniko.data.api.ReleaseApi
import com.aniko.data.api.ReleaseCommentApi
import com.aniko.data.api.ScheduleApi
import com.aniko.data.api.SearchApi
import com.aniko.data.locale.LocaleStore
import com.aniko.data.repository.AuthRepository
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.data.repository.ProfileRepository
import com.aniko.data.repository.ReleaseRepository
import com.aniko.data.repository.ScheduleRepository
import com.aniko.data.session.SessionStore
import com.aniko.data.sync.SyncQueueWorker
import com.aniko.network.ApiConfig
import com.aniko.network.SessionInvalidator
import com.aniko.network.TokenProvider
import com.aniko.network.createAnixHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Koin-модуль data-слоя.
 *
 * `Settings` сюда НЕ входит: его реализация платформенная (Android требует Context),
 * поэтому его поставляет `composeApp` через свой платформенный модуль.
 */
val dataModule =
    module {
        single<Clock> { Clock.System }
        single<CoroutineDispatcher>(named("io")) { Dispatchers.IO }

        single { ApiConfig() }

        single { SessionStore(settings = get(), secureStorage = get()) }
        single<TokenProvider> { get<SessionStore>() }
        single<SessionInvalidator> { get<SessionStore>() }
        single { LocaleStore(settings = get()) }

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

        single { AuthRepository(authApi = get(), sessionStore = get()) }
        single {
            ReleaseRepository(
                releaseApi = get(),
                searchApi = get(),
                releaseCacheStore = get(),
                releaseListStore = get(),
                listMembershipStore = get(),
                clock = get(),
            )
        }
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
