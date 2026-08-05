package com.anixkmp.app.di

import com.anixkmp.app.feature.home.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * DI-граф фичевого слоя.
 *
 * Фичи живут пакетами внутри `composeApp` (не отдельными Gradle-модулями) — так решено
 * в плане ради скорости соло-разработки. Когда модуль перерастёт себя,
 * пакет `feature/<name>` вынимается в отдельный Gradle-модуль почти без правок.
 */
val appModule = module {
    viewModelOf(::HomeViewModel)
}
