package com.anixkmp.data.di

import com.anixkmp.data.api.AuthApi
import com.anixkmp.data.api.EpisodeApi
import com.anixkmp.data.api.FavoriteApi
import com.anixkmp.data.api.HistoryApi
import com.anixkmp.data.api.ProfileListApi
import com.anixkmp.data.api.ReleaseApi
import com.anixkmp.data.api.SearchApi
import com.anixkmp.data.repository.AuthRepository
import com.anixkmp.data.repository.EpisodeRepository
import com.anixkmp.data.repository.LibraryRepository
import com.anixkmp.data.repository.ReleaseRepository
import com.anixkmp.data.session.SessionStore
import com.anixkmp.network.ApiConfig
import com.anixkmp.network.SessionInvalidator
import com.anixkmp.network.TokenProvider
import com.anixkmp.network.createAnixHttpClient
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

    single { AuthRepository(authApi = get(), sessionStore = get()) }
    single { ReleaseRepository(releaseApi = get(), searchApi = get()) }
    single { EpisodeRepository(episodeApi = get()) }
    single { LibraryRepository(profileListApi = get(), favoriteApi = get(), historyApi = get()) }
}
