package com.aniko.app.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.model.Achievement
import com.aniko.model.FriendRequestVisibility
import com.aniko.model.PrivacyVisibility
import com.aniko.model.ProfileDetails
import com.aniko.model.ProfilePrivacy
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixAvatar
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.component.AnixThemePicker
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
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
 * Загрузка/ошибка — тот же паттерн `AnixLoadingBox`/`AnixErrorBox`, что `LibraryScreen`, только
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
 *   путь на `SettingsScreen` (см. её KDoc про то, что она больше не таб-бара);
 * - [themeMode]/[onThemeModeChange] переехали сюда вместе с `AnixThemePicker` (физически теперь
 *   рисуется в [ProfileContent] прямо под шапкой, как в мокапе) — раньше их держал `SettingsScreen`.
 */
@Suppress("LongParameterList") // themeMode/onThemeModeChange добавлены аддитивно к уже
// существовавшему плоскому набору параметров (тот же случай, что и `AnixSessionGate`/
// `SettingsScreen`) — группировка в data class ради обхода линта добавила бы косвенность без
// пользы, экран остаётся тонким прокси без собственного стейта темы (владелец — `ThemeStore`).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onReleaseClick: (Int) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOpenLists: () -> Unit = {},
    themeMode: String? = null,
    onThemeModeChange: (String?) -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    Scaffold(
        modifier = modifier.testTag(AnixTestTags.PROFILE_SCREEN_ROOT),
        topBar = {
            TopAppBar(
                title = { Text(strings.profileTitle) },
                actions = {
                    // Подтверждено на устройстве (Фаза 11, T9): IconButton не сливает
                    // Icon.contentDescription в свой кликабельный узел.
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = strings.settingsTitle },
                    ) {
                        AnixIcon(name = "settings", contentDescription = null, filled = true)
                    }
                },
            )
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
                            themeMode = themeMode,
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
                    onThemeModeChange = onThemeModeChange,
                    onOpenLists = onOpenLists,
                    modifier = contentModifier,
                )
        }
    }
}

/**
 * `profile`/`privacy`/`achievements`/`themeMode` одной группой — иначе [ProfileContent] превышала
 * бы detekt `LongParameterList` вместе с [ProfilePrivacyCallbacks]/`onReleaseClick`/
 * `onThemeModeChange`/`modifier`. [themeMode] добавлен в P13.T2 вместе с переездом
 * `AnixThemePicker` на этот экран — сгруппирован с остальным read-only состоянием профиля по той
 * же причине, что и остальные три поля.
 */
private data class ProfileContentData(
    val profile: ProfileDetails,
    val privacy: ProfilePrivacy,
    val achievements: List<Achievement>,
    val themeMode: String?,
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
 * P13.T2/T11 (сверка с мокапом Claude Design) добавили сюда переключатель темы сразу под шапкой
 * ([ProfileThemeSection]) — раньше он жил на `SettingsScreen`, мокап рисует его прямо под шапкой
 * профиля. [AchievementsSection] осталась на прежнем месте относительно остальных секций (порядок
 * `Header → Theme → Highlights → FavoriteGenres → Achievements → Stats → Charts → RecentlyWatched →
 * Privacy`) — план не требовал её перемещать, только подтвердить, что она есть в новой компоновке.
 */
@Suppress("LongParameterList") // Тот же координирующий блок, что и `ProfileScreen` — см. её
// KDoc/Suppress. `onOpenLists` (P13, точное соответствие макету: ссылка "My Lists →" под именем в
// шапке) добавлен аддитивно к уже сгруппированным data/callbacks-параметрам.
@Composable
private fun ProfileContent(
    data: ProfileContentData,
    callbacks: ProfilePrivacyCallbacks,
    onReleaseClick: (Int) -> Unit,
    onThemeModeChange: (String?) -> Unit,
    onOpenLists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Не деструктуризация (`val (a, b, c, d) = data`): у detekt `DestructuringDeclarationWithTooManyEntries`
    // лимит 3 компонента, а после P13.T2 в [ProfileContentData] их четыре.
    val profile = data.profile
    val privacy = data.privacy
    val achievements = data.achievements
    val themeMode = data.themeMode
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val windowSize = LocalAnixWindowSize.current
    val sectionPadding = Modifier.padding(horizontal = dimens.spaceM)

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = dimens.contentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
        ) {
            ProfileHeader(profile = profile, onOpenLists = onOpenLists, modifier = sectionPadding)

            ProfileThemeSection(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                modifier = sectionPadding,
            )

            ProfileHighlights(profile = profile, modifier = sectionPadding)

            FavoriteGenresSection(profile = profile, modifier = sectionPadding)

            AchievementsSection(achievements = achievements)

            HorizontalDivider(modifier = sectionPadding)

            Text(
                text = strings.profileStatsTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = sectionPadding,
            )
            StatsGrid(profile = profile, modifier = sectionPadding)

            ProfileChartsSection(profile = profile, windowSize = windowSize, modifier = sectionPadding)

            RecentlyWatchedSection(profile = profile, onReleaseClick = onReleaseClick)

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

/**
 * Переключатель темы — переехал сюда из `SettingsScreen` в P13.T2 (сверка с мокапом Claude
 * Design: мокап рисует Theme прямо под шапкой профиля). Заголовок переиспользует
 * [Strings.settingsTheme] — тот же текст, что раньше стоял над `AnixThemePicker` на
 * `SettingsScreen`, ключ не дублировался под новое место специально.
 */
@Composable
private fun ProfileThemeSection(
    themeMode: String?,
    onThemeModeChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(
            text = strings.settingsTheme,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        AnixThemePicker(currentMode = themeMode, onSelect = onThemeModeChange)
    }
}

/**
 * Аватар + логин + ссылка «Мои списки →» + (опционально) бейдж спонсора и баннер бана.
 *
 * [onOpenLists] — Track A (точное соответствие макету): мокап рисует кликабельную ссылку "My
 * Lists →" прямо под именем в шапке профиля, ведущую на тот же экран, что и вкладка таб-бара
 * `Library` (см. [com.aniko.app.navigation.AnixSection.Library]) — колбэк подключает координатор
 * (`App.kt`), как и остальные навигационные колбэки этого экрана ([onSettingsClick]-подобные).
 */
@Suppress("LongMethod") // Шапка целиком повторяет структуру макета (аватар/имя/ссылка/бейджи/
// бан-баннер) одной функцией; вынос опциональных блоков добавил бы косвенность ради счётчика строк
// (тот же приём, что и в остальных Track-A секциях этого файла).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeader(
    profile: ProfileDetails,
    onOpenLists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        AnixAvatar(avatarUrl = profile.avatarUrl, login = profile.login, size = AVATAR_SIZE)
        Text(
            text = profile.login,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = PROFILE_NAME_FONT_SIZE),
            fontWeight = FontWeight.ExtraBold,
        )

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
