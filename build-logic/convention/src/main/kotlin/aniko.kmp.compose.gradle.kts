/**
 * Подключает Compose Multiplatform к KMP-модулю.
 * Применяется поверх `aniko.kmp.library` (или поверх `com.android.application` в composeApp).
 */
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}
