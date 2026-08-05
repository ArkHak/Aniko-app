package com.anixkmp.app.di

import com.anixkmp.data.di.dataModule
import com.russhwolf.settings.Settings
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Платформенная часть DI-графа.
 *
 * Здесь живёт всё, что нельзя создать в commonMain: [Settings] (Android требует `Context`),
 * позже — платформенный плеер и системные сервисы.
 */
expect fun platformModule(): Module

/**
 * Единая точка старта Koin.
 *
 * Android вызывает её из `Application.onCreate` с `androidContext { ... }`,
 * Desktop — из `main()`, iOS — из `MainViewController()`.
 */
fun initKoin(config: KoinAppDeclaration? = null): KoinApplication = startKoin {
    config?.invoke(this)
    modules(platformModule(), dataModule, appModule)
}

private var koinStarted = false

/** Идемпотентный запуск: iOS может пересоздавать `MainViewController`. */
fun initKoinOnce(config: KoinAppDeclaration? = null) {
    if (koinStarted) return
    koinStarted = true
    initKoin(config)
}
