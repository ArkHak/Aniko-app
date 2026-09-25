package com.aniko.app.di

/**
 * `true` — debug-сборка (разработка), `false` — release (то, что ставится пользователю).
 *
 * Единственный источник правды о типе сборки для commonMain: от него зависят опции, которые
 * нельзя оставлять включёнными в release (сейчас — HTTP-логирование, см. `ApiConfig.enableLogging`
 * в `appModule`). Реализации платформенные, потому что «release» у платформ устроен по-разному:
 * - Android — `BuildConfig.DEBUG` варианта сборки (debug/release build type);
 * - iOS — `Platform.isDebugBinary` (Xcode-конфигурация Debug/Release пробрасывается в Gradle
 *   через `embedAndSignAppleFrameworkForXcode`);
 * - Desktop — запущенное из установленного (`jpackage`) приложения считается release.
 */
expect fun isDebugBuild(): Boolean
