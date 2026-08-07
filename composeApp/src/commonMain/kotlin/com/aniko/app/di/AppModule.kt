package com.aniko.app.di

import com.aniko.app.feature.auth.LoginViewModel
import com.aniko.app.feature.home.HomeViewModel
import com.aniko.app.feature.library.LibraryViewModel
import com.aniko.app.feature.player.PlayerViewModel
import com.aniko.app.feature.profile.ProfileViewModel
import com.aniko.app.feature.release.ReleaseDetailsViewModel
import com.aniko.app.feature.search.SearchViewModel
import com.aniko.app.feature.settings.SettingsViewModel
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
        viewModelOf(::HomeViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::ReleaseDetailsViewModel)
        viewModelOf(::PlayerViewModel)
        viewModelOf(::LibraryViewModel)
        viewModelOf(::ProfileViewModel)
    }
