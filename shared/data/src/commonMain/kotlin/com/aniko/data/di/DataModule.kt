package com.aniko.data.di

import com.aniko.data.api.AuthApi
import com.aniko.data.api.EpisodeApi
import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.ProfileApi
import com.aniko.data.api.ProfileListApi
import com.aniko.data.api.ProfilePreferenceApi
import com.aniko.data.api.ReleaseApi
import com.aniko.data.api.SearchApi
import com.aniko.data.repository.AuthRepository
import com.aniko.data.repository.EpisodeRepository
import com.aniko.data.repository.LibraryRepository
import com.aniko.data.repository.ProfileRepository
import com.aniko.data.repository.ReleaseRepository
import com.aniko.data.session.SessionStore
import com.aniko.network.ApiConfig
import com.aniko.network.SessionInvalidator
import com.aniko.network.TokenProvider
import com.aniko.network.createAnixHttpClient
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Koin-модуль data-слоя.
 *
 * `Settings` сюда НЕ входит: его реализация платформенная (Android требует Context),
 * поэтому его поставляет `composeApp` через свой платформенный модуль.
 */
val dataModule = module {
    single { ApiConfig() }

    single { SessionStore(settings = get(), secureStorage = get()) }
    single<TokenProvider> { get<SessionStore>() }
    single<SessionInvalidator> { get<SessionStore>() }

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

    single { AuthRepository(authApi = get(), sessionStore = get()) }
    single { ReleaseRepository(releaseApi = get(), searchApi = get()) }
    single { EpisodeRepository(episodeApi = get()) }
    single { LibraryRepository(profileListApi = get(), favoriteApi = get(), historyApi = get()) }
    single { ProfileRepository(profileApi = get(), profilePreferenceApi = get(), sessionStore = get()) }
}
