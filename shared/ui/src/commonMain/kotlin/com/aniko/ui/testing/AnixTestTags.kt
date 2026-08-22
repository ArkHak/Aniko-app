package com.aniko.ui.testing

/**
 * Стабильные Compose `testTag()`-идентификаторы для UI/смоук-тестов (F4, Фаза 11 —
 * `docs/REELWAVE_PLAN.md`).
 *
 * Живёт в `shared/ui`, а не в `composeApp`, потому что элементы нижней навигации
 * ([BOTTOM_NAV_BAR]/[bottomNavItem]) размечаются внутри `shared/ui`
 * (`com.aniko.ui.adaptive.AnixNavigationBar`), а корневые контейнеры экранов — в `composeApp`
 * (пакеты `feature`); общий модуль избегает циклической зависимости composeApp → shared/ui → composeApp.
 *
 * Область НАМЕРЕННО ограничена корневыми контейнерами экранов и элементами нижней навигации —
 * это общий фундамент (F1-F4), разделяемый несколькими независимыми треками Фазы 11, которые
 * позже параллельно работают над теми же файлами экранов. Тегов на элементы ВНУТРИ экранов
 * (кнопки, поля, карточки списков и т.п.) здесь намеренно нет — их добавляет тот трек, которому
 * они конкретно нужны, в своей собственной работе, чтобы не плодить лишние диффы в файлах,
 * которые правит одновременно кто-то ещё.
 */
object AnixTestTags {
    // --- Корневые контейнеры экранов (composeApp/.../feature/*) ---

    /** Корень [com.aniko.app.feature.auth.LoginScreen]. */
    const val LOGIN_SCREEN_ROOT: String = "login_screen_root"

    /** Корень [com.aniko.app.feature.home.HomeScreen]. */
    const val HOME_SCREEN_ROOT: String = "home_screen_root"

    /** Корень [com.aniko.app.feature.search.SearchScreen] (Catalog). */
    const val SEARCH_SCREEN_ROOT: String = "search_screen_root"

    /** Корень [com.aniko.app.feature.schedule.ScheduleScreen]. */
    const val SCHEDULE_SCREEN_ROOT: String = "schedule_screen_root"

    /** Корень [com.aniko.app.feature.library.LibraryScreen]. */
    const val LIBRARY_SCREEN_ROOT: String = "library_screen_root"

    /** Корень [com.aniko.app.feature.settings.SettingsScreen]. */
    const val SETTINGS_SCREEN_ROOT: String = "settings_screen_root"

    /** Корень [com.aniko.app.feature.settings.NotificationSettingsScreen]. */
    const val NOTIFICATION_SETTINGS_SCREEN_ROOT: String = "notification_settings_screen_root"

    /** Корень [com.aniko.app.feature.profile.ProfileScreen]. */
    const val PROFILE_SCREEN_ROOT: String = "profile_screen_root"

    /** Корень [com.aniko.app.feature.gallery.TokenGalleryScreen]. */
    const val TOKEN_GALLERY_SCREEN_ROOT: String = "token_gallery_screen_root"

    /** Корень [com.aniko.app.feature.release.ReleaseDetailsScreen]. */
    const val RELEASE_DETAILS_SCREEN_ROOT: String = "release_details_screen_root"

    /** Корень [com.aniko.app.feature.comments.ReleaseCommentsScreen]. */
    const val RELEASE_COMMENTS_SCREEN_ROOT: String = "release_comments_screen_root"

    /** Корень [com.aniko.app.feature.player.PlayerScreen]. */
    const val PLAYER_SCREEN_ROOT: String = "player_screen_root"

    // --- Нижняя навигация (com.aniko.ui.adaptive.AnixNavigationBar, AnixWindowSize.Compact) ---

    /** Сам контейнер `NavigationBar`. */
    const val BOTTOM_NAV_BAR: String = "bottom_nav_bar"

    /**
     * Тег одного пункта нижней навигации.
     *
     * @param itemId [com.aniko.ui.adaptive.AdaptiveNavItem.id] — в приложении это имя
     * `com.aniko.app.navigation.AnixSection` (`"Home"`, `"Search"`, `"Schedule"`, `"Library"`,
     * `"Settings"`).
     */
    fun bottomNavItem(itemId: String): String = "bottom_nav_item_$itemId"
}
