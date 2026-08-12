package com.aniko.ui.i18n

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Все локализуемые UI-строки приложения (Фаза 2 плана, P2.T9).
 *
 * Один плоский `data class`, а не набор вложенных groups: у detekt [LongParameterList]
 * `ignoreDataClasses = true` в дефолтной конфигурации (проверено `default-detekt-config.yml`
 * detekt 1.23.8), поэтому большой конструктор `data class` не нарушение — а плоская структура
 * проще для greenfield-набора из ~90 ключей, чем продумывание вложенной группировки заранее.
 * Секции разделены комментариями по фиче/экрану, часть ключей намеренно переиспользуется
 * между экранами (например [listStatusWatching] — и вкладка «Мои списки», и статистика профиля,
 * и статус-чипы карточки релиза используют один и тот же текст).
 *
 * Форматируемые строки ([libraryMoveToStatus], [releaseEpisodeFallbackName], [playerSourceError])
 * — функции-поля, а не шаблоны со плейсхолдерами: в commonMain нет `String.format`
 * (JVM-only API), а ручная замена токенов — лишний слой поверх того, что и так тривиально
 * выражается лямбдой.
 */
@Immutable
data class Strings(
    // --- Общее ---
    val commonRetry: String,
    val commonErrorNoConnection: String,
    val commonErrorUnauthorized: String,
    val commonAccountBanned: String,
    val commonAddToFavorites: String,
    val commonRemoveFromFavorites: String,
    val commonFavoriteBadge: String,
    // --- Навигация / каркас приложения ---
    val navHome: String,
    val navSearch: String,
    val navLibrary: String,
    val navSettings: String,
    val navSchedule: String,
    val sessionExpiredMessage: String,
    val backContentDescription: String,
    // --- Статусы списка (ListStatus) — переиспользуются в Library/Release/Profile/ReleaseCard ---
    val listStatusWatching: String,
    val listStatusPlanned: String,
    val listStatusCompleted: String,
    val listStatusOnHold: String,
    val listStatusDropped: String,
    val listStatusShortWatching: String,
    val listStatusShortPlanned: String,
    val listStatusShortOnHold: String,
    val listStatusShortDropped: String,
    // --- Вход ---
    val loginTitle: String,
    val loginLoginLabel: String,
    val loginPasswordLabel: String,
    val loginSubmit: String,
    val loginGenericError: String,
    val loginInvalidLogin: String,
    val loginInvalidPassword: String,
    // --- Главная ---
    val homeContinueWatching: String,
    val homeRecommendations: String,
    val homeSectionLoadError: String,
    // --- Мои списки ---
    val libraryTabFavorites: String,
    val libraryTabHistory: String,
    val libraryEmptyStatus: String,
    val libraryEmptyFavorites: String,
    val libraryEmptyHistory: String,
    val libraryLoadError: String,
    val libraryRemoveFromList: String,
    val libraryRemoveFromHistory: String,
    val libraryMoveToStatus: (status: String) -> String,
    // --- Карточка релиза ---
    val releaseInfoYear: String,
    val releaseInfoStatus: String,
    val releaseInfoEpisodesLabel: String,
    val releaseInfoRating: String,
    val releaseSectionVoiceType: String,
    val releaseSectionSource: String,
    val releaseSectionEpisodesList: String,
    val releaseEpisodeFallbackName: (position: Int) -> String,
    val releaseStatusAnnounce: String,
    val releaseStatusOngoing: String,
    val releaseStatusFinished: String,
    val releaseLoadError: String,
    val releaseEpisodesLoadError: String,
    // --- Поиск ---
    val searchPlaceholder: String,
    val searchEmptyPrompt: String,
    val searchError: String,
    val searchNoResults: String,
    // --- Профиль ---
    val profileTitle: String,
    val profileLoadError: String,
    val profileSponsorBadge: String,
    val profileAccountBannedPermanently: String,
    val profileStatsTitle: String,
    val profilePrivacyTitle: String,
    val profileFriendsLabel: String,
    val profileCommentsLabel: String,
    val privacyWhoSeesStats: String,
    val privacyWhoSeesLists: String,
    val privacyWhoSeesSocial: String,
    val privacyFriendRequestsLabel: String,
    val privacyIncognitoMode: String,
    val privacyVisibilityEveryone: String,
    val privacyVisibilityFriendsOnly: String,
    val privacyVisibilityOnlyMe: String,
    val friendRequestVisibilityNobody: String,
    // --- Плеер ---
    val playerLoadError: String,
    val playerSourceError: (hostKey: String) -> String,
    // --- Настройки ---
    val settingsMyProfile: String,
    val settingsSignOut: String,
    val settingsDesignGallery: String,
    val settingsLanguage: String,
    // --- Экран-галерея токенов (P2.T12) ---
    val galleryTitle: String,
    val galleryColorsSection: String,
    val galleryTypographySection: String,
    val gallerySpacingSection: String,
    val galleryRadiusSection: String,
    val galleryLanguageLabel: String,
    val galleryThemeLabel: String,
    val galleryThemeLight: String,
    val galleryThemeDark: String,
    val galleryLanguageSystem: String,
    // P6.T12: витрина компонентов Фазы 6 + переключатель масштаба шрифта.
    val galleryComponentsSection: String,
    val galleryFontScaleLabel: String,
    // --- Фаза 5 — адаптивный каркас ---
    // Заголовок экрана расписания (AnixDestination.Schedule).
    val scheduleTitle: String,
    // Заголовок экрана комментариев к релизу (AnixDestination.ReleaseComments).
    val commentsTitle: String,
    // Плейсхолдер detail-панели на wide-экранах (ListDetailPaneScaffold, P5.T3), когда список
    // ничего не выбрал.
    val detailPaneEmptyTitle: String,
    val detailPaneEmptyMessage: String,
    // --- Desktop-меню (macOS `MenuBar`, P5.T6) ---
    val menuAbout: String,
    val menuQuit: String,
    val menuView: String,
    val menuLanguage: String,
    val menuGo: String,
    val menuBack: String,
    // --- Фаза 6 — библиотека компонентов ---
    val badgeNewEpisode: String,
    val badgeComingSoon: String,
    val badgeRatingContentDescription: (grade: String) -> String,
    val badgeNewEpisodeContentDescription: String,
    val episodeWatchedContentDescription: String,
    val episodeUnwatchedContentDescription: String,
    val railShowAll: String,
    val filterChipAll: String,
    val filterChipReset: String,
    val chartNoData: String,
    val chartLegendOther: String,
    val ratingHistogramTitle: String,
    val ratingYourScore: String,
    val ratingStarsContentDescription: (stars: Int) -> String,
    val progressEpisodesOf: (watched: Int, total: Int) -> String,
)

/** Дефолт — [EnStrings]: тот же выбор, что `defaultLanguageTag = "en"` в `ProvideAppStrings`. */
val LocalStrings = staticCompositionLocalOf { EnStrings }
