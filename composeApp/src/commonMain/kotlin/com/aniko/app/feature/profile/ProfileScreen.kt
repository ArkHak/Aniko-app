// Экран профиля разложен на маленькие приватные composable по секциям макета (шапка/тема/
// статистика/приватность/бейджи топбара, P13/P16.T18) — декомпозиция в пользу читаемости, а не
// разрастание ответственности одного файла (тот же приём, что `ProfileStatsSections.kt`).
@file:Suppress("TooManyFunctions")

package com.aniko.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.data.profileshowcase.LocalProfilePinnedSectionStore
import com.aniko.data.profileshowcase.ProfileShowcaseSection
import com.aniko.model.Achievement
import com.aniko.model.FriendRequestVisibility
import com.aniko.model.PrivacyVisibility
import com.aniko.model.ProfileDetails
import com.aniko.model.ProfilePrivacy
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.adaptive.LocalGlassBottomInset
import com.aniko.ui.component.AnixAsyncImage
import com.aniko.ui.component.AnixAvatar
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.ChipRow
import com.aniko.ui.component.ExpandedScreenTitle
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран «Мой профиль».
 *
 * Фаза 7 дала шапку (аватар/логин/спонсор-бан), сетку статистики и privacy-настройки; Фаза 9
 * (P9.T7-T13, кроме вырезанного P9.T10) добавила поверх этого:
 * - акцентированные «часы просмотра» и «серий просмотрено» ([ProfileHighlights]) — раньше часы
 *   вообще не показывались, хотя `watched_time` мапился с Фазы 7;
 * - любимые жанры прямо из `preferred_genres` (вердикт P0.T4 — считать локально не нужно);
 * - donut-график распределения по спискам (P9.T8) и недельный график активности (P9.T9) —
 *   готовые компоненты `:shared:ui` (P6.T10), данные из `watch_dynamics`;
 * - ленту «Недавно смотрели» (P9.T11) из `history[]` того же ответа `profile/{id}`;
 * - гостевое состояние (P9.T12) вместо генерик-ошибки, см. KDoc [ProfileViewModel];
 * - адаптив (P9.T13): контент ограничен `contentMaxWidth` и центрирован, а два графика на
 *   Expanded встают в две колонки вместо одной длинной ленты.
 *
 * Загрузка/ошибка — тот же паттерн `AnixLoadingState`/`AnixErrorState`, что `LibraryScreen`, только
 * на весь экран (а не поверх уже отрисованного списка), так как профиль — не пагинируемая лента,
 * а одна карточка данных.
 *
 * [onReleaseClick] сознательно НЕ идёт через `LocalTitleNavigator`: на wide-экранах тот открывает
 * тайтл в detail-панели, а маршрут профиля (`chromeRoutes` в `App.kt`) в `ListDetailHost` не
 * завёрнут — панели там негде отрисоваться, и клик оказался бы «мёртвым». Поэтому переход —
 * всегда полноэкранный маршрут, его подключает `App.kt`.
 *
 * P13.T2 (сверка с мокапом Claude Design) сделала этот экран прямой вкладкой таб-бара
 * ([com.aniko.app.navigation.AnixSection.Profile], person-иконка) вместо дочернего экрана
 * `Settings`. Как следствие:
 * - `TopAppBar` больше не показывает кнопку «назад» (`onBack` убран из параметров) — этот экран
 *   теперь таб-рут, как `Home`/`Library`/`Schedule`, у которых своей кнопки назад тоже нет;
 * - вместо этого в `actions` появилась шестерёнка ([onSettingsClick]) — единственный оставшийся
 *   путь на `SettingsScreen` (см. её KDoc про то, что она больше не таб-бара).
 *
 * P13.T2 также переносила сюда переключатель темы (`AnixThemePicker`) вместе с `AchievementsSection`
 * под мокап Claude Design. Живой фидбег пользователя (2026-09-11) развернул именно эту часть
 * решения: тема вернулась на `SettingsScreen` (см. её KDoc) — `ProfileScreen` больше не принимает
 * `themeMode`/`onThemeModeChange` и не рисует переключатель темы.
 *
 * Unified Expanded title (2026-09-18): на [com.aniko.ui.adaptive.AnixWindowSize.Expanded] заголовок
 * страницы — [ExpandedScreenTitle] (тот же атом, что у Каталога/Библиотеки/Расписания), встроенный
 * в контент ([ProfileContent]), а не текст `TopAppBar.title`. С 2026-09-18 (живой фидбек
 * пользователя) на Expanded `TopAppBar` не рисуется вовсе: колокольчик уведомлений и шестерёнка
 * настроек переехали в футер сайдбара (`SidebarChromeActions` в `App.kt`), и контент профиля
 * поднялся к верху окна. На Compact/Medium `TopAppBar` с заголовком и [ProfileTopBarActions]
 * остаётся — там сайдбара нет.
 */
@Suppress("LongMethod", "LongParameterList") // Экран остаётся тонким прокси без собственного
// стейта; оставшихся параметров (5 колбэков + viewModel) всё ещё достаточно, чтобы сработало
// detekt-правило LongParameterList, даже после удаления themeMode/onThemeModeChange —
// группировка в data class ради обхода линта добавила бы косвенность без пользы (тот же
// аргумент, что и раньше).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onOpenLists: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val windowSize = LocalAnixWindowSize.current

    // Обновляет бейдж непрочитанных уведомлений при каждой рекомпозиции экрана — в т.ч. при
    // возврате из `NotificationsScreen` (см. KDoc `ProfileUiState.unreadNotificationsCount`).
    LaunchedEffect(Unit) { viewModel.refreshUnreadNotifications() }

    Scaffold(
        modifier = modifier.testTag(AnixTestTags.PROFILE_SCREEN_ROOT),
        topBar = {
            // Desktop (Expanded): топбар не рисуется вовсе — заголовок живёт в контенте как
            // [ExpandedScreenTitle] (см. KDoc экрана выше и `ProfileContent`), а колокольчик/
            // шестерёнка переехали в футер сайдбара (`SidebarChromeActions`, `App.kt`). Без
            // топбара контент поднимается к верху окна (запрос пользователя 2026-09-18).
            if (windowSize != AnixWindowSize.Expanded) {
                TopAppBar(
                    title = { Text(strings.profileTitle) },
                    actions = {
                        ProfileTopBarActions(
                            unreadNotificationsCount = uiState.unreadNotificationsCount,
                            onNotificationsClick = onNotificationsClick,
                            onSettingsClick = onSettingsClick,
                        )
                    },
                    // Живой фидбек пользователя (2026-09-11, «белая полоска над шапкой профиля»):
                    // M3 TopAppBar красится в свой дефолтный containerColor (surface), который не
                    // совпадает с фоном тела экрана (`colorScheme.background` — iOS grouped
                    // background, см. KDoc `AppTheme`) — рассинхрон давал видимую полосу. Явно
                    // красим шапку в тот же фон, что и страницу, чтобы она визуально сливалась с
                    // телом (тот же приём — ниже, `SettingsTopBar` в `SettingsScreen.kt`).
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
    ) { innerPadding ->
        val profile = uiState.profile
        val privacy = uiState.privacy
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)

        when {
            uiState.isGuest -> ProfileGuestBox(onSignIn = viewModel::signOut, modifier = contentModifier)

            uiState.isLoading && profile == null -> AnixLoadingState(modifier = contentModifier)

            uiState.error != null && profile == null ->
                AnixErrorState(
                    // P2.T10: не показываем `error.message` напрямую — это текст исключения
                    // AnixError (технический, на английском, только для логов/debug), не
                    // локализованный UI-текст. Всегда локализованный fallback.
                    message = strings.profileLoadError,
                    onRetry = viewModel::retry,
                    modifier = contentModifier,
                )

            profile != null && privacy != null ->
                ProfileContent(
                    data =
                        ProfileContentData(
                            profile = profile,
                            privacy = privacy,
                            achievements = uiState.achievements,
                        ),
                    callbacks =
                        ProfilePrivacyCallbacks(
                            onUpdateStats = viewModel::updatePrivacyStats,
                            onUpdateCounts = viewModel::updatePrivacyCounts,
                            onUpdateSocial = viewModel::updatePrivacySocial,
                            onUpdateFriendRequests = viewModel::updatePrivacyFriendRequests,
                            onToggleIncognito = viewModel::toggleIncognito,
                        ),
                    onReleaseClick = onReleaseClick,
                    onOpenLists = onOpenLists,
                    modifier = contentModifier,
                )
        }
    }
}

/** Кламп на «99+» — Material `Badge` не резиновый по ширине под произвольное число цифр, тот же
 *  предел, что у большинства бейджей уведомлений в других приложениях. */
private fun unreadCountLabel(count: Long): String {
    val capped = count > UNREAD_BADGE_MAX
    return if (capped) UNREAD_BADGE_LABEL else count.toString()
}

private const val UNREAD_BADGE_MAX = 99L
private const val UNREAD_BADGE_LABEL = "99+"

/**
 * Колокольчик уведомлений (P16.T18, с бейджем непрочитанных при `unreadNotificationsCount > 0`) +
 * шестерёнка настроек. Вынесена из [ProfileScreen] отдельной функцией — иначе `TopAppBar.actions`
 * лямбда сама по себе превышала detekt `LongMethod`.
 */
@Composable
private fun ProfileTopBarActions(
    unreadNotificationsCount: Long,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val strings = LocalStrings.current

    // Подтверждено на устройстве (Фаза 11, T9): IconButton не сливает Icon.contentDescription в
    // свой кликабельный узел.
    IconButton(
        onClick = onNotificationsClick,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = strings.notificationsIconContentDescription
            },
    ) {
        if (unreadNotificationsCount > 0) {
            BadgedBox(badge = { Badge { Text(unreadCountLabel(unreadNotificationsCount)) } }) {
                AnixIcon(name = "notifications", contentDescription = null)
            }
        } else {
            AnixIcon(name = "notifications", contentDescription = null)
        }
    }
    IconButton(
        onClick = onSettingsClick,
        modifier = Modifier.clearAndSetSemantics { contentDescription = strings.settingsTitle },
    ) {
        AnixIcon(name = "settings", contentDescription = null, filled = true)
    }
}

/**
 * `profile`/`privacy`/`achievements` одной группой — иначе [ProfileContent] тащила бы их по
 * отдельности вместе с [ProfilePrivacyCallbacks]/`onReleaseClick`/`modifier` (detekt
 * `LongParameterList`). Раньше сюда же входил `themeMode` (P13.T2 переезд `AnixThemePicker` на
 * этот экран) — поле убрано вместе с переключателем темы, который вернулся на `SettingsScreen`
 * по запросу пользователя (2026-09-11, см. KDoc [ProfileScreen]).
 */
private data class ProfileContentData(
    val profile: ProfileDetails,
    val privacy: ProfilePrivacy,
    val achievements: List<Achievement>,
)

/**
 * Пять privacy-колбэков одной группой — иначе [ProfileContent] и [PrivacySection] тащили бы их
 * по отдельности через два уровня (detekt `LongParameterList`).
 */
private data class ProfilePrivacyCallbacks(
    val onUpdateStats: (PrivacyVisibility) -> Unit,
    val onUpdateCounts: (PrivacyVisibility) -> Unit,
    val onUpdateSocial: (PrivacyVisibility) -> Unit,
    val onUpdateFriendRequests: (FriendRequestVisibility) -> Unit,
    val onToggleIncognito: () -> Unit,
)

/**
 * Текущий пин + колбэк переключения одной группой (P16.T13) — тот же приём группировки, что
 * [ProfilePrivacyCallbacks]: без неё каждая из четырёх движимых секций
 * ([FavoriteGenresSection]/[AchievementsSection]/[RecentlyWatchedSection]/`StatsGrid`-блок)
 * тащила бы [ProfileShowcaseSection]`?` и колбэк раздельно. [onTogglePin] — `suspend`, а не
 * fire-and-forget лямбда: вызывающая сторона ([ProfileSectionTitleRow]) сама решает, в каком
 * `CoroutineScope` его запустить (тот же паттерн, что `VoiceTypePinButton.onToggle` в
 * `ReleaseEpisodesSection.kt`).
 */
internal data class ProfilePinState(
    val pinnedSection: ProfileShowcaseSection?,
    val onTogglePin: suspend (ProfileShowcaseSection) -> Unit,
)

/**
 * Гостевое состояние (P9.T12): не сетевая ошибка, а понятное приглашение войти. Кнопка сбрасывает
 * остатки сессии — на экран логина уводит `AnixSessionGate` (см. KDoc [ProfileViewModel.signOut]).
 */
@Composable
private fun ProfileGuestBox(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(dimens.spaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
        ) {
            Text(
                text = strings.profileGuestTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = strings.profileGuestMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onSignIn) { Text(strings.loginSubmit) }
        }
    }
}

/**
 * Вертикальная лента секций профиля. Горизонтальные отступы навешивают сами секции, а не общий
 * `Column` — «Недавно смотрели» (`LazyRow`) обязана скроллиться от края до края, как рельсы на
 * главном экране.
 *
 * P13.T2/T11 (сверка с мокапом Claude Design) добавляли сюда переключатель темы сразу под шапкой —
 * живой фидбек пользователя (2026-09-11) развернул это решение: тема вернулась на `SettingsScreen`
 * (см. её KDoc), здесь секции темы больше нет. Порядок секций теперь: `Header → Highlights →
 * FavoriteGenres → Achievements → Stats → Charts → RecentlyWatched → Privacy`. [AchievementsSection]
 * осталась на прежнем месте относительно остальных секций — план не требовал её перемещать.
 *
 * P16.T13 (локальный пин секции витрины) добавил переупорядочивание: если пользователь закрепил
 * одну из четырёх движимых секций ([ProfileShowcaseSection] — жанры/достижения/связка
 * `StatsGrid`+`ProfileChartsSection`/«недавно смотрели»), она рендерится СРАЗУ после
 * [ProfileHeader] (перед «часами просмотра»), а остальные три — следом, в исходном порядке. Без
 * пина (`pinnedSection == null`) порядок совпадает с описанным выше дефолтом байт-в-байт:
 * `section != pinnedSection` истинно для абсолютно всех секций, `pinnedBlock == null` ничего не
 * рендерит.
 */
@Suppress("LongMethod") // Тот же координирующий блок, что и `ProfileScreen` — см. её KDoc/Suppress.
// `onOpenLists` (P13) добавлен аддитивно к уже сгруппированным data/callbacks-параметрам;
// P16.T13 добавил локальный пин-стор и сборку движимых секций в список — оба аддитивны к уже
// существовавшей структуре функции, не новая ответственность.
@Composable
private fun ProfileContent(
    data: ProfileContentData,
    callbacks: ProfilePrivacyCallbacks,
    onReleaseClick: (Int) -> Unit,
    onOpenLists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = data.profile
    val privacy = data.privacy
    val achievements = data.achievements
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val windowSize = LocalAnixWindowSize.current
    val isExpanded = windowSize == AnixWindowSize.Expanded
    val sectionPadding = if (isExpanded) Modifier else Modifier.padding(horizontal = dimens.spaceM)

    val pinnedSectionStore = koinInject<LocalProfilePinnedSectionStore>()
    val pinnedSection by pinnedSectionStore.pinnedSection().collectAsStateWithLifecycle(initialValue = null)
    val pinState =
        remember(pinnedSection, pinnedSectionStore) {
            ProfilePinState(pinnedSection = pinnedSection, onTogglePin = pinnedSectionStore::toggle)
        }

    // Четыре движимые секции (P16.T13) в исходном порядке — при пине одна из них выносится сразу
    // после шапки, остальные остаются здесь же, в этом же порядке. Каждый блок — самодостаточная
    // Composable-лямбда: несёт собственные отступы/заголовок/пин-кнопку (и, для статистики,
    // ведущий разделитель), поэтому безопасен к перемещению в начало ленты без потери контекста.
    val movableSections: List<Pair<ProfileShowcaseSection, @Composable () -> Unit>> =
        listOf(
            ProfileShowcaseSection.FAVORITE_GENRES to {
                FavoriteGenresSection(profile = profile, pinState = pinState, modifier = sectionPadding)
            },
            ProfileShowcaseSection.ACHIEVEMENTS to {
                AchievementsSection(achievements = achievements, pinState = pinState, windowSize = windowSize)
            },
            ProfileShowcaseSection.STATISTICS to {
                HorizontalDivider(modifier = sectionPadding)
                ProfileSectionTitleRow(
                    title = strings.profileStatsTitle,
                    section = ProfileShowcaseSection.STATISTICS,
                    pinState = pinState,
                    modifier = sectionPadding,
                )
                StatsGrid(profile = profile, windowSize = windowSize, modifier = sectionPadding)
                ProfileChartsSection(profile = profile, windowSize = windowSize, modifier = sectionPadding)
            },
            ProfileShowcaseSection.RECENTLY_WATCHED to {
                RecentlyWatchedSection(
                    profile = profile,
                    onReleaseClick = onReleaseClick,
                    pinState = pinState,
                    windowSize = windowSize,
                )
            },
        )
    val pinnedBlock = movableSections.firstOrNull { it.first == pinnedSection }?.second

    // Общая часть для обеих раскладок — ширина/скролл/вертикальные отступы. Горизонтальный
    // spaceM Expanded-ветки НЕ здесь (см. ниже): раньше он висел прямо на этом Column и
    // одинаково давил на все дети, включая заголовок — с приходом [ExpandedScreenTitle] (он же
    // несёт свой spaceM по горизонтали) это удвоило бы отступ заголовка. Отступ переехал на
    // вложенный `Column` с остальными секциями (см. `sectionsModifier` ниже), заголовок
    // остаётся вне него — тот же приём, что уже применён в Catalog/Library/Schedule.
    val columnModifier =
        Modifier
            .fillMaxSize()
            .widthIn(max = dimens.contentMaxWidth)
            .verticalScroll(rememberScrollState())
            // Liquid Glass (2026-09-11): нижний паддинг учитывает высоту плавающего таб-бара —
            // см. KDoc `LocalGlassBottomInset`/аналогичное место в `HomeScreen.kt`. Профиль —
            // таб-рут (см. KDoc [ProfileScreen] про P13.T2), поэтому у него тот же bottom bar
            // под ним, что и у остальных корневых вкладок (в т.ч. на Expanded).
            //
            // Верхний паддинг — только на Compact/Medium (там заголовок живёт в `TopAppBar`, и
            // контенту нужен воздух под ним). На Expanded он убран (2026-09-18): заголовок —
            // [ExpandedScreenTitle] со встроенным вертикальным `spaceM`, и лишний `top = spaceM`
            // на колонке опускал заголовок профиля ниже, чем на Каталоге/Библиотеке/Расписании.
            .padding(
                top = if (isExpanded) 0.dp else dimens.spaceM,
                bottom = dimens.spaceM + LocalGlassBottomInset.current,
            )

    // Раньше Expanded-ветка обнуляла горизонтальные отступы секций (`sectionPadding = Modifier`)
    // и не добавляла свой — контент печатался вплотную к краям окна (ревью F5, 2026-09-17).
    // Остальные Expanded-экраны (Home/Schedule) используют `spaceM` по горизонтали — тот же
    // токен и здесь, только на уровне секций, не заголовка (см. комментарий выше).
    val sectionsModifier = if (isExpanded) Modifier.padding(horizontal = dimens.spaceM) else Modifier

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(modifier = columnModifier) {
            // Desktop (Expanded): единый заголовок-страница [ExpandedScreenTitle] — тот же атом,
            // что и у Каталога/Библиотеки/Расписания (см. его KDoc), встроенный в контент вместо
            // прежнего `TopAppBar.title` (см. KDoc [ProfileScreen]). На Compact/Medium заголовок
            // остаётся в `TopAppBar`, здесь ничего не рисуется.
            if (isExpanded) {
                ExpandedScreenTitle(text = strings.profileTitle)
            }

            Column(
                modifier = sectionsModifier,
                horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
            ) {
                ProfileHeader(
                    profile = profile,
                    onOpenLists = onOpenLists,
                    windowSize = windowSize,
                    modifier = sectionPadding,
                )

                pinnedBlock?.invoke()

                ProfileHighlights(profile = profile, modifier = sectionPadding)

                movableSections.forEach { (section, block) ->
                    if (section != pinnedSection) block()
                }

                HorizontalDivider(modifier = sectionPadding)

                Text(
                    text = strings.profilePrivacyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = sectionPadding,
                )
                PrivacySection(privacy = privacy, callbacks = callbacks, modifier = sectionPadding)
            }
        }
    }
}

/**
 * Аватар + логин + ссылка «Мои списки →» + (опционально) бейдж спонсора и баннер бана.
 *
 * [onOpenLists] — Track A (точное соответствие макету): мокап рисует кликабельную ссылку "My
 * Lists →" прямо под именем в шапке профиля, ведущую на тот же экран, что и вкладка таб-бара
 * `Library` (см. [com.aniko.app.navigation.AnixSection.Library]) — колбэк подключает координатор
 * (`App.kt`), как и остальные навигационные колбэки этого экрана ([onSettingsClick]-подобные).
 *
 * P16.T13 добавил два аддитивных, независимо отказоустойчивых штриха «витрины»:
 * - [ProfileDetails.coverUrl] (`theme_background_url`), если задан — рисуется баннером высотой
 *   [PROFILE_COVER_HEIGHT] с аватаром поверх (`Box` + `Alignment.Center`); `null` — баннер просто
 *   не рендерится, обычный аватар без изменений (см. KDoc `coverUrl`: у всех живых проб зеркала
 *   пока `null`, поле — задел под пользователей с настроенной темой витрины).
 * - Акцентное кольцо вокруг аватара по топ-любимому жанру ([profileGenreAccentColor]) — тоже
 *   аддитивно: без любимых жанров ([ProfileDetails.preferredGenres] пуст) кольца нет вообще, тот
 *   же `Modifier` без `border`.
 */
@Suppress("LongMethod") // Шапка целиком повторяет структуру макета (аватар/имя/ссылка/бейджи/
// бан-баннер) одной функцией; вынос опциональных блоков добавил бы косвенность ради счётчика строк
// (тот же приём, что и в остальных Track-A секциях этого файла). P16.T13 добавил обложку/акцент
// аддитивно к уже существовавшему набору опциональных блоков — та же логика.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeader(
    profile: ProfileDetails,
    onOpenLists: () -> Unit,
    windowSize: AnixWindowSize,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val isExpanded = windowSize == AnixWindowSize.Expanded
    val topGenreName = profile.preferredGenres.maxByOrNull { it.percentage }?.name
    val genreAccent = profileGenreAccentColor(topGenreName, AnixThemeTokens.colors.chartSeries)
    val avatarModifier =
        if (genreAccent != null) {
            Modifier
                .border(PROFILE_AVATAR_ACCENT_WIDTH, genreAccent, CircleShape)
                .padding(PROFILE_AVATAR_ACCENT_GAP)
        } else {
            Modifier
        }
    val avatarSize = if (isExpanded) AVATAR_SIZE_EXPANDED else AVATAR_SIZE
    val nameFontSize = if (isExpanded) PROFILE_NAME_FONT_SIZE_EXPANDED else PROFILE_NAME_FONT_SIZE

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isExpanded) PROFILE_HEADER_GAP_EXPANDED else dimens.spaceS),
    ) {
        val coverUrl = profile.coverUrl
        if (isExpanded && coverUrl == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PROFILE_HEADER_GAP_EXPANDED),
            ) {
                AnixAvatar(
                    avatarUrl = profile.avatarUrl,
                    login = profile.login,
                    size = avatarSize,
                    modifier = avatarModifier,
                )
                Text(
                    text = profile.login,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = nameFontSize),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        } else {
            if (coverUrl != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(PROFILE_COVER_HEIGHT)
                            .clip(RoundedCornerShape(dimens.cornerL)),
                    contentAlignment = Alignment.Center,
                ) {
                    AnixAsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    AnixAvatar(
                        avatarUrl = profile.avatarUrl,
                        login = profile.login,
                        size = avatarSize,
                        modifier = avatarModifier,
                    )
                }
            } else {
                AnixAvatar(
                    avatarUrl = profile.avatarUrl,
                    login = profile.login,
                    size = avatarSize,
                    modifier = avatarModifier,
                )
            }
            Text(
                text = profile.login,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = nameFontSize),
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // Подтверждено паттерном Фазы 11 (T9, см. `ReleaseDetailsScreen.CommentsLinkRow`): Text
        // внутри кликабельного Row не сливается с ним сам по себе, поэтому семантика
        // переустанавливается вручную на весь Row.
        Row(
            modifier =
                Modifier
                    .clickable(onClick = onOpenLists)
                    .clearAndSetSemantics { contentDescription = strings.navLibrary },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            Text(
                text = strings.navLibrary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            AnixIcon(
                name = "arrow_forward",
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(PROFILE_LISTS_LINK_ICON_SIZE),
            )
        }

        if (profile.isSponsor) {
            AssistChip(onClick = {}, label = { Text(strings.profileSponsorBadge) })
        }

        if (profile.isBanned || profile.isPermBanned) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(dimens.spaceM)) {
                    Text(
                        text =
                            if (profile.isPermBanned) {
                                strings.profileAccountBannedPermanently
                            } else {
                                strings.commonAccountBanned
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    val banReason = profile.banReason
                    if (!banReason.isNullOrBlank()) {
                        Text(
                            text = banReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(
    privacy: ProfilePrivacy,
    callbacks: ProfilePrivacyCallbacks,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        PrivacyRow(title = strings.privacyWhoSeesStats, selected = privacy.stats, onSelect = callbacks.onUpdateStats)
        PrivacyRow(title = strings.privacyWhoSeesLists, selected = privacy.counts, onSelect = callbacks.onUpdateCounts)
        PrivacyRow(title = strings.privacyWhoSeesSocial, selected = privacy.social, onSelect = callbacks.onUpdateSocial)
        FriendRequestPrivacyRow(
            selected = privacy.friendRequests,
            onSelect = callbacks.onUpdateFriendRequests,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = strings.privacyIncognitoMode, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = privacy.isIncognito, onCheckedChange = { callbacks.onToggleIncognito() })
        }
    }
}

@Composable
private fun PrivacyRow(
    title: String,
    selected: PrivacyVisibility,
    onSelect: (PrivacyVisibility) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        ChipRow(
            items = PrivacyVisibility.entries,
            isSelected = { it == selected },
            label = { it.toDisplayName(strings) },
            onClick = onSelect,
        )
    }
}

@Composable
private fun FriendRequestPrivacyRow(
    selected: FriendRequestVisibility,
    onSelect: (FriendRequestVisibility) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(text = strings.privacyFriendRequestsLabel, style = MaterialTheme.typography.bodyMedium)
        ChipRow(
            items = FriendRequestVisibility.entries,
            isSelected = { it == selected },
            label = { it.toDisplayName(strings) },
            onClick = onSelect,
        )
    }
}

private fun PrivacyVisibility.toDisplayName(strings: Strings): String =
    when (this) {
        PrivacyVisibility.EVERYONE -> strings.privacyVisibilityEveryone
        PrivacyVisibility.FRIENDS_ONLY -> strings.privacyVisibilityFriendsOnly
        PrivacyVisibility.ONLY_ME -> strings.privacyVisibilityOnlyMe
    }

private fun FriendRequestVisibility.toDisplayName(strings: Strings): String =
    when (this) {
        FriendRequestVisibility.EVERYONE -> strings.privacyVisibilityEveryone
        FriendRequestVisibility.NOBODY -> strings.friendRequestVisibilityNobody
    }

// Track A (точное соответствие макету, 2026-09-04): аватар 64dp (был 96dp), имя 17px/800 —
// точные значения макета, не из `AnixDimens`/`anixTypography` (нет готовых слотов под эти
// конкретные px, тот же приём, что и `ACHIEVEMENT_BADGE_SIZE` в `ProfileStatsSections.kt`).
private val AVATAR_SIZE = 64.dp
private val PROFILE_NAME_FONT_SIZE = 17.sp
private val PROFILE_LISTS_LINK_ICON_SIZE = 14.dp

/** [ProfileHeader] — обложка витрины (P16.T13): высота баннера, аватар рисуется поверх центром. */
private val PROFILE_COVER_HEIGHT = 96.dp

/** [ProfileHeader] — акцентное кольцо аватара по топ-любимому жанру (P16.T13). */
private val PROFILE_AVATAR_ACCENT_WIDTH = 2.dp
private val PROFILE_AVATAR_ACCENT_GAP = 3.dp

/** Desktop-вариант [ProfileHeader]: аватар 72dp, имя 20px/800, gap 16dp (мокап строка 875). */
private val AVATAR_SIZE_EXPANDED = 72.dp
private val PROFILE_NAME_FONT_SIZE_EXPANDED = 20.sp
private val PROFILE_HEADER_GAP_EXPANDED = 16.dp
