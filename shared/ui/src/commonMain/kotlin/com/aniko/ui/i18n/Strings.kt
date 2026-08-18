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
    // P9.T1: заголовок вкладки со счётчиком ("Смотрю (12)"). P9.T2: content description для
    // тулбара shuffle/реверс над сеткой.
    val libraryTabCountFormat: (title: String, count: Int) -> String,
    val libraryShuffle: String,
    val libraryReverseSort: String,
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
    // Фаза 9 (P9.T7-T13). «Смотрю/В планах/Просмотрено/Отложено/Брошено» для легенды donut'а и
    // «Избранное» для плитки переиспользуют [listStatusWatching]/.../[libraryTabFavorites] —
    // новых ключей под них здесь сознательно нет.
    val profileWatchedHoursLabel: String,
    val profileWatchedHoursValue: (hours: Int) -> String,
    val profileWatchedEpisodesLabel: String,
    val profileListsChartTitle: String,
    val profileListsChartTotalLabel: String,
    val profileActivityTitle: String,
    val profileFavoriteGenresTitle: String,
    val profileGenrePercent: (name: String, percentage: Int) -> String,
    val profileRecentlyWatchedTitle: String,
    val profileRecentlyWatchedEmpty: String,
    val profileGuestTitle: String,
    val profileGuestMessage: String,
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
    // Оверлей плеера (P8.T3/T4/T5/T8). Кнопка «назад» переиспользует [backContentDescription].
    // Аудиодорожки/субтитров/качества здесь нет намеренно и не появится: это внутренний UI
    // чужого embed-плеера (CUT в таблице аудита `docs/REELWAVE_PLAN.md`), и заводить под них
    // ключи значило бы пообещать в UI то, чего в приложении нет.
    val playerPlay: String,
    val playerPause: String,
    val playerSeekBackward: String,
    val playerSeekForward: String,
    val playerPictureInPicture: String,
    val playerSpeedLabel: String,
    val playerSpeedValue: (rate: String) -> String,
    val playerNextEpisodeIn: (seconds: Int) -> String,
    val playerNextEpisodeNow: String,
    val playerNextEpisode: String,
    val playerCancel: String,
    val playerMarkWatched: String,
    val playerMarkUnwatched: String,
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
    // --- Фаза 9 (P9.T4-T5) — доработка экрана расписания: локализованные дни недели
    // (раньше был сырой `WeekDay.name`) + пустое состояние дня без релизов ---
    val scheduleDayMonday: String,
    val scheduleDayTuesday: String,
    val scheduleDayWednesday: String,
    val scheduleDayThursday: String,
    val scheduleDayFriday: String,
    val scheduleDaySaturday: String,
    val scheduleDaySunday: String,
    val scheduleEmptyDayMessage: String,
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
    // --- Фаза 7 — общий фундамент экранов Home/Catalog/Title Detail/Rating/Comments ---
    // Заведено разом для всех 5 параллельных треков (P7.T1-T13), чтобы не было конфликтов
    // ключей при одновременной работе. Часть строк из мокапа сознательно переиспользует уже
    // существующие ключи выше (не дублируется здесь): "Продолжить смотреть" — [homeContinueWatching],
    // "Рекомендации"/раздел рекомендаций главного экрана — [homeRecommendations], статус-чипы
    // каталога (анонс/онгоинг/завершён) — [releaseStatusAnnounce]/[releaseStatusOngoing]/
    // [releaseStatusFinished], "Ваша оценка" — [ratingYourScore], 5 подписей списков сообщества
    // на экране Rating ("Смотрят"/"В планах"/"Просмотрено"/"Отложено"/"Брошено") —
    // [listStatusWatching]/[listStatusPlanned]/[listStatusCompleted]/[listStatusOnHold]/
    // [listStatusDropped] (см. `ListStatusStrings.kt` — 4 из 5 совпадают дословно, у Watching
    // русский текст в форме 1-го лица "Смотрю"; так как английский текст "Watching" совпадает
    // 1:1 в обоих контекстах, реюз принят без нового ключа).
    // Home (P7.T1-T2)
    val homeBannerTitle: String,
    val homeQuickActionCatalog: String,
    val homeQuickActionSchedule: String,
    val homeQuickActionLibrary: String,
    val homeQuickActionRandom: String,
    val homeSectionDiscussing: String,
    val homeSectionNewEpisodes: String,
    // Catalog (P7.T3-T6)
    val catalogTabAll: String,
    val catalogTabNew: String,
    val catalogFiltersTitle: String,
    val catalogFiltersReset: String,
    val catalogFiltersApply: String,
    val catalogViewGrid: String,
    val catalogViewList: String,
    val catalogEmptyResults: String,
    // Title Detail (P7.T7-T13)
    val titleDetailWatch: String,
    val titleDetailScreenshots: String,
    val titleDetailSimilar: String,
    val titleDetailRecommended: String,
    val releaseCommentsTitle: (count: Int) -> String,
    val titleDetailStudio: String,
    val titleDetailCountry: String,
    val titleDetailDirector: String,
    val titleDetailAuthor: String,
    val titleDetailSeason: String,
    val titleDetailReleaseDate: String,
    val titleDetailAgeRating: String,
    val titleDetailEpisodeDuration: String,
    val titleDetailCategory: String,
    val titleDetailSource: String,
    val titleDetailTranslators: String,
    // Rating
    val ratingVoteCount: (count: Int) -> String,
    val ratingRemoveVote: String,
    // Comments
    val commentsSpoilerLabel: String,
    val commentsEmpty: String,
    val commentsSortNewest: String,
    val commentsSortOldest: String,
    val commentReplyCount: (count: Int) -> String,
    // --- Player + выбор озвучки (P8.T6) ---
    // "All" переиспользует уже существующий [filterChipAll] (тот же смысл — сбросить фильтр).
    val releaseVoiceFilterDub: String,
    val releaseVoiceFilterSub: String,
    val releaseEpisodesCount: (count: Int) -> String,
    val releaseVoiceTypeSameCast: (name: String) -> String,
    val releaseVoiceTypeViewsContentDescription: (count: Int) -> String,
    val badgeSub: String,
    val badgeSubContentDescription: String,
)

/** Дефолт — [EnStrings]: тот же выбор, что `defaultLanguageTag = "en"` в `ProvideAppStrings`. */
val LocalStrings = staticCompositionLocalOf { EnStrings }
