package com.aniko.ui.i18n

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Все локализуемые UI-строки приложения (Фаза 2 плана, P2.T9).
 *
 * Один плоский `interface`, а не набор вложенных groups — `EnStrings`/`RuStrings` реализуют его
 * анонимными `object : Strings { ... }`. Изначально (до Фазы 11) это был `data class` с тем же
 * плоским набором полей в первичном конструкторе: детект-правило [LongParameterList] это не ловит
 * (`ignoreDataClasses = true` в `default-detekt-config.yml`), но на ~257 ключах конструктор данных
 * упёрся в реальный лимит JVM — 255 параметров на метод/конструктор (`java.lang.ClassFormatError:
 * Too many arguments in method signature` при загрузке класса на JVM/Android, найдено фундаментом
 * Фазы 11 при попытке отрендерить `App()` в Compose UI test). `interface` с `override val` в
 * реализациях не собирает поля в один конструктор — тот же плоский плейсхолдер API
 * (`LocalStrings.current.xxx`), без лимита на количество ключей.
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
interface Strings {
    // --- Общее ---
    val commonRetry: String
    val commonErrorNoConnection: String
    val commonErrorUnauthorized: String
    val commonAccountBanned: String
    val commonAddToFavorites: String
    val commonRemoveFromFavorites: String
    val commonFavoriteBadge: String

    // --- Навигация / каркас приложения ---
    // P13.T1 (сверка с мокапом Claude Design): порядок вкладок здесь = порядок в UI (см. KDoc
    // `AnixSection` в `composeApp`) — Home → Catalog → Library → Schedule → Profile.
    // `navCatalog` — переименован из `navSearch` (единственное использование — лейбл вкладки таб-
    // бара в `App.kt:toNavItem`, Kotlin-константа `AnixSection.Search` не переименована, см. её
    // KDoc). `navProfile` — новый ключ, вкладка `Settings` больше не в таб-баре (см. `settingsTitle`
    // ниже в разделе «Настройки»).
    val navHome: String
    val navCatalog: String
    val navLibrary: String
    val navSchedule: String
    val navProfile: String
    val sessionExpiredMessage: String
    val backContentDescription: String

    // --- Статусы списка (ListStatus) — переиспользуются в Library/Release/Profile/ReleaseCard ---
    val listStatusWatching: String
    val listStatusPlanned: String
    val listStatusCompleted: String
    val listStatusOnHold: String
    val listStatusDropped: String
    val listStatusShortWatching: String
    val listStatusShortPlanned: String
    val listStatusShortOnHold: String
    val listStatusShortDropped: String

    // --- Вход ---
    val loginTitle: String
    val loginLoginLabel: String
    val loginPasswordLabel: String
    val loginSubmit: String
    val loginShowPassword: String
    val loginHidePassword: String
    val loginGenericError: String
    val loginInvalidLogin: String
    val loginInvalidPassword: String

    // --- Главная ---
    val homeContinueWatching: String
    val homeSectionLoadError: String

    /** Сверка с макетом Claude Design (phone, 2026-09-08): приветствие под брендом на Home
     *  ("Good evening"/«Добрый вечер») — по времени суток текущей локали, см. HomeScreen. */
    val homeGreetingMorning: String
    val homeGreetingDay: String
    val homeGreetingEvening: String
    val homeGreetingNight: String

    /** Catalog (2026-09-08): мета-строка результата «N ep · ★ R» — ep и рейтинг форматирует
     *  вызывающая сторона (как [libraryItemsCountFormat]). */
    val catalogMetaFormat: (episodes: Int, rating: String) -> String

    // --- Мои списки ---
    val libraryTabFavorites: String
    val libraryTabHistory: String
    val libraryEmptyStatus: String
    val libraryEmptyFavorites: String
    val libraryEmptyHistory: String
    val libraryLoadError: String
    val libraryRemoveFromList: String
    val libraryRemoveFromHistory: String
    val libraryMoveToStatus: (status: String) -> String

    // P9.T1: заголовок вкладки со счётчиком ("Смотрю (12)"). P9.T2: content description для
    // тулбара shuffle/реверс над сеткой.
    val libraryTabCountFormat: (title: String, count: Int) -> String
    val libraryShuffle: String
    val libraryReverseSort: String

    /** Track A (сверка Compact-раскладки, 2026-09-04): счётчик "N titles" слева в [LibraryToolbar]
     *  (макет Claude Design) — отдельный от [libraryTabCountFormat] ключ: тот форматирует заголовок
     *  вкладки ("Смотрю (12)"), этот — отдельную строку тулбара над списком ("12 тайтлов"). */
    val libraryItemsCountFormat: (count: Int) -> String

    // --- Карточка релиза ---
    val releaseInfoYear: String
    val releaseInfoStatus: String
    val releaseInfoEpisodesLabel: String
    val releaseInfoRating: String
    val releaseSectionVoiceType: String
    val releaseSectionSource: String
    val releaseSectionEpisodesList: String
    val releaseEpisodeFallbackName: (position: Int) -> String
    val releaseStatusAnnounce: String
    val releaseStatusOngoing: String
    val releaseStatusFinished: String
    val releaseLoadError: String

    /** P13.T7: отдельная строка для провала под-запроса расширенных метаданных
     *  (`ReleaseHeaderSection`'s `detailsError`, независим от базового [releaseLoadError]) —
     *  раньше переиспользовал тот же текст "Не удалось загрузить релиз", хотя сам релиз к этому
     *  моменту уже отрисован целиком (постер/жанры/эпизоды), см. KDoc `toReleaseMessage`. */
    val releaseDetailsLoadError: String
    val releaseEpisodesLoadError: String

    // --- Поиск ---
    val searchPlaceholder: String
    val searchEmptyPrompt: String
    val searchError: String
    val searchNoResults: String

    // --- Профиль ---
    val profileTitle: String
    val profileLoadError: String
    val profileSponsorBadge: String
    val profileAccountBannedPermanently: String
    val profileStatsTitle: String
    val profilePrivacyTitle: String
    val profileFriendsLabel: String
    val profileCommentsLabel: String

    // Фаза 9 (P9.T7-T13). «Смотрю/В планах/Просмотрено/Отложено/Брошено» для легенды donut'а и
    // «Избранное» для плитки переиспользуют [listStatusWatching]/.../[libraryTabFavorites] —
    // новых ключей под них здесь сознательно нет.
    val profileWatchedHoursLabel: String
    val profileWatchedHoursValue: (hours: Int) -> String
    val profileWatchedEpisodesLabel: String
    val profileListsChartTitle: String
    val profileListsChartTotalLabel: String
    val profileActivityTitle: String
    val profileFavoriteGenresTitle: String
    val profileGenrePercent: (name: String, percentage: Int) -> String
    val profileRecentlyWatchedTitle: String
    val profileRecentlyWatchedEmpty: String
    val profileAchievementsTitle: String

    /** Кнопка-пин заголовка секции витрины профиля (P16.T13, локальный пин — см. KDoc
     *  `LocalProfilePinnedSectionStore`). Параметризовано названием секции, чтобы
     *  content-description оставался осмысленным для скринридера при любой из закрепляемых секций. */
    val profilePinSectionAction: (title: String) -> String
    val profileUnpinSectionAction: (title: String) -> String
    val profileGuestTitle: String
    val profileGuestMessage: String
    val privacyWhoSeesStats: String
    val privacyWhoSeesLists: String
    val privacyWhoSeesSocial: String
    val privacyFriendRequestsLabel: String
    val privacyIncognitoMode: String
    val privacyVisibilityEveryone: String
    val privacyVisibilityFriendsOnly: String
    val privacyVisibilityOnlyMe: String
    val friendRequestVisibilityNobody: String

    // --- Плеер ---
    val playerLoadError: String
    val playerSourceError: (hostKey: String) -> String

    // Оверлей плеера (P8.T3/T4/T5/T8). Кнопка «назад» переиспользует [backContentDescription].
    // Качество по-прежнему CUT (см. KDoc `PlayerBottomPanel` в `PlayerOverlay.kt` и отчёт
    // P13.T9 в `docs/REELWAVE_PLAN.md`): сегмент качества в Kodik embed-URL декоративный на нашей
    // стороне, своя кнопка переключения либо ничего не даст, либо сломает подпись ссылки — ключей
    // под него нет и не будет. Аудиодорожка (P13.T10), наоборот, теперь есть — это не UI чужого
    // плеера, а выбор ОЗВУЧКИ/типа перевода на уровне Anixart API (`episode/{releaseId}/{typeId}`,
    // тот же список, что и на Title Detail), просто перенесённый в плеер отдельным чипом.
    val playerPlay: String
    val playerPause: String
    val playerSeekBackward: String
    val playerSeekForward: String
    val playerPictureInPicture: String
    val playerSpeedValue: (rate: String) -> String
    val playerNextEpisodeIn: (seconds: Int) -> String
    val playerNextEpisodeNow: String
    val playerNextEpisode: String
    val playerCancel: String
    val playerMarkWatched: String
    val playerMarkUnwatched: String

    // Чип «Audio» (P13.T10) — переключение озвучки без выхода из плеера. [playerAudioLabel] служит
    // и заголовком пикера, и подписью чипа, пока текущая озвучка ещё не определилась
    // (`PlayerUiState.currentVoiceType == null`, см. её KDoc про подбор по `sourceId`).
    val playerAudioLabel: String
    val playerAudioChipLabel: (name: String) -> String

    // Компактный (не полноэкранный) режим плеера по умолчанию (P13, сверка с мокапом) — кнопка
    // разворота в полноэкранный режим ([playerEnterFullscreen]).
    val playerEnterFullscreen: String

    // P16-фикс 2026-09-09 — качество видео (чип нижней панели + заголовок пикера).
    val playerQualityChip: (String) -> String
    val playerQualityTitle: String

    // P16 (2026-09-10) — скорость одним табом (заголовок пикера; лейбл — playerSpeedValue).
    val playerSpeedTitle: String

    // P16.T9 — вертикальные жесты полноэкранного плеера: подписи индикатора уровня.
    val playerBrightnessLabel: String
    val playerVolumeLabel: String

    // P16.T7 — resume-диалог: продолжить с сохранённой позиции или начать сначала.
    val playerResumeTitle: String
    val playerResumeContinue: String
    val playerResumeFromStart: String
    val playerResumeContinueFrom: (time: String) -> String

    // --- Настройки ---
    // P13.T2 (сверка с мокапом Claude Design): `settingsTitle` — новый ключ, заголовок `TopAppBar`
    // экрана настроек (раньше отдельного заголовка не было — экран был таб-рутом без `TopAppBar`,
    // теперь это дочерний экран, открываемый шестерёнкой из `ProfileScreen`, см. её KDoc); заодно
    // используется как `contentDescription` этой шестерёнки. `settingsMyProfile` убран — пункт
    // «Мой профиль» удалён из `SettingsScreen` (профиль сам стал вкладкой таб-бара, обратная
    // ссылка была бы циклом). `settingsTheme` остался тем же ключом, хотя `AnixThemePicker`
    // физически переехал на `ProfileScreen` — переиспользуется как заголовок секции темы там же.
    val settingsTitle: String
    val settingsSignOut: String
    val settingsDesignGallery: String
    val settingsLanguage: String
    val settingsTheme: String
    val themeLight: String
    val themeDark: String

    // P16.T21 — секция выбора иконки приложения (Android: activity-alias переключение).
    val settingsAppIcon: String
    val appIconMain: String
    val appIconClassic: String
    val appIconDream: String
    val appIconIce: String

    // --- Экран-галерея токенов (P2.T12) ---
    val galleryTitle: String
    val galleryColorsSection: String
    val galleryTypographySection: String
    val gallerySpacingSection: String
    val galleryRadiusSection: String
    val galleryLanguageLabel: String
    val galleryThemeLabel: String
    val galleryThemeLight: String
    val galleryThemeDark: String
    val galleryLanguageSystem: String

    // P6.T12: витрина компонентов Фазы 6 + переключатель масштаба шрифта.
    val galleryComponentsSection: String
    val galleryFontScaleLabel: String

    // --- Фаза 5 — адаптивный каркас ---
    // Заголовок экрана расписания (AnixDestination.Schedule).
    val scheduleTitle: String

    // --- Фаза 9 (P9.T4-T5) — доработка экрана расписания: локализованные дни недели
    // (раньше был сырой `WeekDay.name`) + пустое состояние дня без релизов ---
    val scheduleDayMonday: String
    val scheduleDayTuesday: String
    val scheduleDayWednesday: String
    val scheduleDayThursday: String
    val scheduleDayFriday: String
    val scheduleDaySaturday: String
    val scheduleDaySunday: String
    val scheduleEmptyDayMessage: String

    // Заголовок экрана комментариев к релизу (AnixDestination.ReleaseComments).
    val commentsTitle: String

    // Плейсхолдер detail-панели на wide-экранах (ListDetailPaneScaffold, P5.T3), когда список
    // ничего не выбрал.
    val detailPaneEmptyTitle: String
    val detailPaneEmptyMessage: String

    // --- Desktop-меню (macOS `MenuBar`, P5.T6) ---
    val menuAbout: String
    val menuQuit: String
    val menuView: String
    val menuLanguage: String
    val menuGo: String
    val menuBack: String

    // --- Фаза 6 — библиотека компонентов ---
    val badgeNewEpisode: String
    val badgeComingSoon: String
    val badgeRatingContentDescription: (grade: String) -> String
    val badgeNewEpisodeContentDescription: String
    val episodeWatchedContentDescription: String
    val episodeUnwatchedContentDescription: String
    val railShowAll: String
    val filterChipAll: String
    val filterChipReset: String
    val chartNoData: String
    val chartLegendOther: String
    val ratingHistogramTitle: String
    val ratingYourScore: String
    val ratingStarsContentDescription: (stars: Int) -> String
    val progressEpisodesOf: (watched: Int, total: Int) -> String

    // --- Фаза 7 — общий фундамент экранов Home/Catalog/Title Detail/Rating/Comments ---
    // Заведено разом для всех 5 параллельных треков (P7.T1-T13), чтобы не было конфликтов
    // ключей при одновременной работе. Часть строк из мокапа сознательно переиспользует уже
    // существующие ключи выше (не дублируется здесь): "Продолжить смотреть" — [homeContinueWatching]
    // статус-чипы каталога (анонс/онгоинг/завершён) — [releaseStatusAnnounce]/[releaseStatusOngoing]/
    // [releaseStatusFinished], "Ваша оценка" — [ratingYourScore], 5 подписей списков сообщества
    // на экране Rating ("Смотрят"/"В планах"/"Просмотрено"/"Отложено"/"Брошено") —
    // [listStatusWatching]/[listStatusPlanned]/[listStatusCompleted]/[listStatusOnHold]/
    // [listStatusDropped] (см. `ListStatusStrings.kt` — 4 из 5 совпадают дословно, у Watching
    // русский текст в форме 1-го лица "Смотрю"; так как английский текст "Watching" совпадает
    // 1:1 в обоих контекстах, реюз принят без нового ключа).
    // Home (P7.T1-T2). Track C (2026-09-04, точное соответствие макету): [homeQuickActionCatalog]
    // переименован в [homeQuickActionPopular] ("Popular"/"Популярное"), [homeQuickActionLibrary] —
    // в [homeQuickActionFilter] ("Filters"/"Фильтры"); [homeSectionDiscussing] переименован в
    // [homeTopWeek] ("Top This Week"/"Топ недели") — те же данные (`discussing`), новый заголовок
    // рельсы под макет; [homeRecommendations] (была своя рельса-паджинатор) удалён вместе с
    // рендером самой рельсы — мёртвый ключ нигде больше не использовался.
    val homeBannerTitle: String
    val homeQuickActionPopular: String
    val homeQuickActionSchedule: String
    val homeQuickActionFilter: String
    val homeQuickActionRandom: String
    val homeQuickActionFeed: String
    val homeQuickActionCollections: String
    val homeTopWeek: String
    val homeSectionNewEpisodes: String

    // Catalog (P7.T3-T6)
    val catalogTabAll: String
    val catalogTabNew: String

    // P16.T1 — верхние табы каталога «Аниме/Дунхуа»: пресет страны релиза, а не сортировка
    // (см. `CatalogContentType`).
    val catalogContentTypeAnime: String
    val catalogContentTypeDonghua: String

    // P16.T2 — «Моя вкладка»: сохранённый набор фильтров каталога и ссылка на него.
    val catalogMyTabApply: String
    val catalogMyTabSave: String
    val catalogMyTabClear: String
    val catalogFilterShare: String
    val catalogFiltersTitle: String
    val catalogFiltersReset: String
    val catalogFiltersApply: String
    val catalogViewGrid: String
    val catalogViewList: String
    val catalogEmptyResults: String

    // Title Detail (P7.T7-T13)
    val titleDetailWatch: String

    // Track A (design-match-remaining-screens, 2026-09-04): заголовок блока синопсиса на
    // phone Compact — раньше текст описания рисовался вообще без подписи (см. журнал ветки).
    val titleDetailSynopsis: String

    // Track A: подпись кнопки "Add to list" на phone Compact-шапке, когда релиз ещё не в
    // списке пользователя ([com.aniko.model.Release.myListStatus] == null) — кнопка открывает то
    // же самое меню статусов, что раньше рисовалось всегда видимым [ChipRow] (см. KDoc
    // `HeroAddToListButton` в `ReleaseHeaderSection.kt`).
    val titleDetailAddToList: String
    val titleDetailScreenshots: String
    val titleDetailSimilar: String
    val titleDetailRecommended: String
    val releaseCommentsTitle: (count: Int) -> String
    val titleDetailStudio: String
    val titleDetailCountry: String
    val titleDetailDirector: String
    val titleDetailAuthor: String
    val titleDetailSeason: String
    val titleDetailReleaseDate: String
    val titleDetailAgeRating: String
    val titleDetailEpisodeDuration: String
    val titleDetailCategory: String
    val titleDetailSource: String
    val titleDetailTranslators: String

    // Rating
    val ratingVoteCount: (count: Int) -> String
    val ratingRemoveVote: String

    // Comments
    val commentsSpoilerLabel: String
    val commentsEmpty: String
    val commentsSortNewest: String
    val commentsSortOldest: String
    val commentReplyCount: (count: Int) -> String
    val commentsComposerPlaceholder: String
    val commentsComposerSubmit: String

    // --- Player + выбор озвучки (P8.T6) ---
    // "All" переиспользует уже существующий [filterChipAll] (тот же смысл — сбросить фильтр).
    val releaseVoiceFilterDub: String
    val releaseVoiceFilterSub: String
    val releaseEpisodesCount: (count: Int) -> String
    val releaseVoiceTypeSameCast: (name: String) -> String
    val releaseVoiceTypeViewsContentDescription: (count: Int) -> String
    val badgeSub: String
    val badgeSubContentDescription: String

    // --- Метки качества и пин озвучки (P16.T4/T6) ---
    val qualityBadge1080p: String
    val qualityBadge1440p: String
    val qualityBadge4k: String
    val releaseVoiceTypePin: String
    val releaseVoiceTypeUnpin: String

    // --- Офлайн-режим (P10.T3) ---
    // Два ключа, а не один: баннер показывает и факт («связи нет»), и следствие («сделанное не
    // потеряется»), иначе пользователь не понимает, можно ли продолжать пользоваться приложением.
    val offlineBannerTitle: String
    val offlineBannerDescription: String

    // --- Share / deep link (P10.T7/P10.T9) ---
    val shareButtonContentDescription: String

    /** Desktop-фоллбэк [com.aniko.ui.share.ShareResult.COPIED_TO_CLIPBOARD]: системного шер-диалога
     * там нет, поэтому "поделиться" копирует ссылку в буфер — экран показывает этот текст снекбаром. */
    val shareLinkCopiedMessage: String

    /**
     * --- Уведомления (P10.T5/P10.T6) ---
     * Тексты самих OS-уведомлений. Собираются вне композиции (фоновый тик синхронизации), язык
     * резолвится через [appStringsFor], а не через LocalStrings, — см. её KDoc.
     *
     * Имя канала уведомлений в системных настройках Android.
     */
    val notificationChannelName: String
    val notificationNewEpisodeTitle: String

    /** Тайтл + серия: «Атака титанов · 5 серия». */
    val notificationNewEpisodeBody: (title: String, episode: String) -> String

    /** Серия неизвестна — сервер прислал уведомление без имени эпизода. */
    val notificationNewEpisodeBodyNoEpisode: (title: String) -> String
    val notificationRelatedReleaseTitle: String
    val notificationRelatedReleaseBody: (title: String) -> String
    val notificationFriendTitle: String
    val notificationFriendBody: (login: String) -> String
    val notificationCommentTitle: String
    val notificationCommentBody: String
    val notificationArticleTitle: String
    val notificationArticleBody: String

    /** Тип уведомления неизвестен этой версии клиента (`AppNotificationKind.UNKNOWN`). */
    val notificationGenericTitle: String
    val notificationGenericBody: String

    // --- Экран уведомлений в приложении (P16.T18), AnixDestination.Notifications ---
    val notificationsTitle: String
    val notificationsEmpty: String
    val notificationsLoadError: String

    // --- Экран ленты (P16.T3), AnixDestination.Feed — read-only MVP, см. отчёт задачи в
    // docs/REELWAVE_PLAN.md: без постинга/голосования/полного rich-контента ---
    val feedTitle: String
    val feedEmpty: String
    val feedLoadError: String

    // --- Экран коллекций (P16.T16), AnixDestination.Collections — read-only MVP, публичный
    // GET collection/all/{page}, см. KDoc CollectionDto (shared/data) ---
    val collectionsTitle: String
    val collectionsEmpty: String
    val collectionsLoadError: String

    fun collectionByCreator(login: String): String

    // --- Экран настроек: секция уведомлений (P10.T6) ---
    val settingsNotificationsSection: String

    /** Пояснение про polling: почему уведомление приходит не мгновенно. */
    val settingsNotificationsPollingNote: String

    /** Android 13+: разрешение POST_NOTIFICATIONS ещё не выдано. */
    val settingsNotificationsPermissionRequired: String
    val settingsNotificationsPermissionGrant: String
    val settingsNotificationsLoadError: String
    val settingsNotificationEpisodes: String
    val settingsNotificationFirstEpisode: String
    val settingsNotificationRelatedReleases: String
    val settingsNotificationArticles: String
    val settingsNotificationComments: String
    val settingsNotificationMyCollectionComments: String
    val settingsNotificationMyArticleComments: String
    val settingsNotificationReportProcess: String

    // --- Фаза 11 (P11.T6/F6) — щедрый набор ключей contentDescription для типовых
    // повторяющихся иконок-без-подписи (иконки навигации/меню/сортировки/лайков и т.п.)
    // которые встречаются на разных экранах. Заведены впрок фундаментом Фазы 11 (T6): сам
    // `contentDescription = stringResource(...)` в конкретные Icon/IconButton эти ключи
    // расставляют профильные треки C (Home/Catalog/Search/Release) / D (Library/Schedule/
    // Profile/Comments) / E (Player/Auth/Settings/Gallery) / F (shared/ui-компоненты) по
    // своим зонам. Треки добавляют СВОИ новые ключи под конкретные экраны в собственных
    // именованных секциях внизу файла — эту секцию не трогают, чтобы не конфликтовать при
    // параллельной работе.
    val searchClearContentDescription: String
    val searchIconContentDescription: String
    val menuContentDescription: String
    val moreOptionsContentDescription: String
    val closeContentDescription: String
    val filterContentDescription: String
    val sortContentDescription: String
    val commentLikeContentDescription: String
    val commentUnlikeContentDescription: String
    val commentReplyContentDescription: String
    val commentReportContentDescription: String
    val expandContentDescription: String
    val collapseContentDescription: String
    val editContentDescription: String
    val deleteContentDescription: String
    val notificationsIconContentDescription: String
    val themeToggleContentDescription: String
    val fullscreenEnterContentDescription: String
    val fullscreenExitContentDescription: String
    val volumeContentDescription: String
    val muteContentDescription: String
    val scrollToTopContentDescription: String
    val avatarContentDescription: String
    val releasePosterContentDescription: (title: String) -> String
    val screenshotThumbnailContentDescription: (position: Int) -> String

    // --- Фаза 11 (T9, на устройстве) — TalkBack на Home нашёл дублирующийся accessibility-label
    // между quick action плиткой "Расписание" и одноимённой вкладкой нижней навигации: два разных
    // кликабельных элемента на одном экране озвучивались одинаково, пользователь TalkBack не мог
    // их различить по звуку. Отдельный ключ с глаголом только для quick action плитки.
    val homeQuickActionOpenContentDescription: (label: String) -> String
}

/** Дефолт — [EnStrings]: тот же выбор, что `defaultLanguageTag = "en"` в `ProvideAppStrings`. */
val LocalStrings = staticCompositionLocalOf { EnStrings }
