package com.aniko.app.navigation

/**
 * Пять секций адаптивного каркаса (P5.T5, трек A) — bottom bar (phone) / nav rail (tablet) /
 * постоянный sidebar (desktop) переключаются по одному и тому же списку секций, различается только
 * визуальное представление. Список секций и их иконки/лейблы для каждого size class собирает
 * интегратор (шаг 6) вместе с `AdaptiveScaffold` трека A — здесь только сопоставление секция →
 * маршрут, без UI.
 */
enum class AnixSection(
    val destination: AnixDestination,
) {
    Home(AnixDestination.Home),
    Search(AnixDestination.Search),
    Schedule(AnixDestination.Schedule),
    Library(AnixDestination.Library),
    Settings(AnixDestination.Settings),
}
