// TooManyFunctions: файл — набор мелких presentation-секций статистики профиля, каждая
// отвечает ровно за один блок макета; выделены сюда из `ProfileScreen.kt` именно затем, чтобы
// тот не разрастался (тот же приём, что и `ReleaseHeaderSection.kt` в Фазе 7).
@file:Suppress("TooManyFunctions")

package com.aniko.app.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.data.profileshowcase.ProfileShowcaseSection
import com.aniko.model.Achievement
import com.aniko.model.ProfileDetails
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.component.AnixAsyncImage
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.StatTileData
import com.aniko.ui.component.TitleCard
import com.aniko.ui.component.chart.BarEntry
import com.aniko.ui.component.chart.ChartSlice
import com.aniko.ui.component.chart.DonutChart
import com.aniko.ui.component.chart.WeeklyBarChart
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Акцентированные метрики: часы просмотра и число просмотренных серий.
 *
 * Часы (`watched_time / 60`, вердикт P0.T4 — сервер отдаёт минуты) вынесены сюда, а не в общую
 * [StatsGrid]: в макете это главная цифра профиля, а восьмым числом в ряду одинаковых плиток она
 * визуально теряется.
 */
@Composable
internal fun ProfileHighlights(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(dimens.spaceM),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
        ) {
            HighlightMetric(
                value = strings.profileWatchedHoursValue(profile.watchedHours),
                label = strings.profileWatchedHoursLabel,
                modifier = Modifier.weight(1f),
            )
            HighlightMetric(
                value = profile.watchedEpisodeCount.toString(),
                label = strings.profileWatchedEpisodesLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HighlightMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Заголовок движимой секции витрины + кнопка-пин (P16.T13, локальный пин — см. KDoc
 * [LocalProfilePinnedSectionStore][com.aniko.data.profileshowcase.LocalProfilePinnedSectionStore]).
 * Тот же визуальный паттерн, что кнопка-булавка озвучки (`VoiceTypePinButton` в
 * `ReleaseEpisodesSection.kt`, P16.T6): `bookmark`, `filled` и цвет `primary` в закреплённом
 * состоянии, иначе тусклая `textSecondary60`. Используется всеми четырьмя движимыми секциями
 * (см. [ProfileShowcaseSection]) — единая точка, чтобы стиль кнопки не разъезжался между ними.
 */
@Composable
internal fun ProfileSectionTitleRow(
    title: String,
    section: ProfileShowcaseSection,
    pinState: ProfilePinState,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val isPinned = pinState.pinnedSection == section

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = { scope.launch { pinState.onTogglePin(section) } },
            modifier = Modifier.size(dimens.minTouchTarget),
        ) {
            AnixIcon(
                name = "bookmark",
                contentDescription =
                    if (isPinned) strings.profileUnpinSectionAction(title) else strings.profilePinSectionAction(title),
                filled = isPinned,
                tint = if (isPinned) MaterialTheme.colorScheme.primary else AnixThemeTokens.colors.textSecondary60,
            )
        }
    }
}

/**
 * Локальный акцент витрины по любимому жанру (P16.T13 «тема витрины по любимым жанрам»).
 *
 * Официальный v10 считает тему на сервере по статистике «просмотрено ≥10» с ежедневным
 * пересчётом (см. `docs/REELWAVE_PLAN.md`, «P16.T13/T15 — механика витрины из строк») —
 * недокументированная серверная логика, воспроизвести её нельзя и не нужно (задание прямо
 * запрещает пытаться повторить её буквально). Здесь — чисто клиентское, детерминированное
 * приближение: цвет берётся из уже существующей категориальной палитры [palette] (на практике —
 * [com.aniko.ui.theme.AnixColors.chartSeries], та же, что красит донат-график распределения
 * списков в [ListsDonutSection]) по хэшу имени топ-жанра. Никаких новых hex не изобретается, а
 * один и тот же жанр у одного пользователя всегда даёт один и тот же индекс палитры (хэш строки
 * детерминирован в рамках одного процесса).
 *
 * Живой баг на реальном устройстве (Pixel 7, реальный аккаунт, топ-жанр «экшен» 13%): у
 * `AnixColors.chartSeries` ПОСЛЕДНИЙ элемент (индекс 5 из 6) — буквально `Color.Gray`, добавленный
 * туда как нейтральный слайс «прочее» для донат-легенды (уместно там, но не здесь). У
 * `"экшен".lowercase().hashCode().mod(6)` результат ровно 5 — то есть кольцо аватара красилось в
 * серый, визуально неотличимо от «акцент не применён», для этого (и статистически ~1 из 6)
 * реальных пользователей. Функция поэтому сама фильтрует [palette] от ахроматичных (серых/
 * чёрных/белых — см. [isAchromatic]) записей ПЕРЕД хэшированием: это деталь реализации именно
 * этой функции (палитра — общий токен для донат-графика, у вызывающего кода нет причины знать,
 * что в ней есть нейтральный «filler»), поэтому фильтр не вынесен на сторону вызова. Если после
 * фильтрации палитра пуста (патологический вход — только серые цвета) — тот же результат, что и
 * при отсутствии любимых жанров: `null`.
 *
 * `null`, если у профиля нет любимых жанров ([topGenreName] == `null`/пусто) или в палитре не
 * осталось ни одного цветного (не ахроматичного) элемента — вызывающий код в этом случае не
 * подкрашивает ничего, оставляя обычные цвета темы.
 *
 * `ReturnCount`: два guard clause (нет жанра / палитра пуста после фильтрации) + основной
 * результат — линейная цепочка читается лучше вложенного `when`, тот же приём, что и
 * `parseDeepLink` в `DeepLink.kt`.
 */
@Suppress("ReturnCount")
internal fun profileGenreAccentColor(
    topGenreName: String?,
    palette: List<Color>,
): Color? {
    if (topGenreName.isNullOrBlank()) return null
    val huePalette = palette.filterNot { it.isAchromatic() }
    if (huePalette.isEmpty()) return null
    val index = topGenreName.lowercase().hashCode().mod(huePalette.size)
    return huePalette[index]
}

/** Порог разброса RGB-каналов, ниже которого цвет считается ахроматичным (серым/чёрным/белым). */
private const val ACHROMATIC_CHROMA_THRESHOLD = 0.02f

/**
 * Ахроматичен ли цвет (серый/чёрный/белый — нет доминирующего тона): каналы R/G/B у таких цветов
 * почти равны. Используется [profileGenreAccentColor], чтобы не выбрать из палитры графика
 * нейтральный «filler»-цвет (живой пример — `Color.Gray` в `AnixColors.chartSeries`, см. её KDoc).
 */
private fun Color.isAchromatic(): Boolean {
    val maxChannel = maxOf(red, green, blue)
    val minChannel = minOf(red, green, blue)
    return maxChannel - minChannel < ACHROMATIC_CHROMA_THRESHOLD
}

/**
 * Любимые жанры — готовое `preferred_genres` с процентами от сервера. Минимальный UI (чипы
 * «жанр · N%») намеренно: своей визуализации распределения жанров макет не требует, а
 * придумывать её сверх того, что отдаёт API, — вне объёма v1.
 */
@Composable
internal fun FavoriteGenresSection(
    profile: ProfileDetails,
    pinState: ProfilePinState,
    modifier: Modifier = Modifier,
) {
    if (profile.preferredGenres.isEmpty()) return

    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        ProfileSectionTitleRow(
            title = strings.profileFavoriteGenresTitle,
            section = ProfileShowcaseSection.FAVORITE_GENRES,
            pinState = pinState,
        )
        // Не `ChipRow`: тот даёт `FilterChip` с семантикой выбора, а здесь чипы чисто
        // информационные — выбирать среди жанров нечего.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            profile.preferredGenres.forEach { genre ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(strings.profileGenrePercent(genre.name, genre.percentage)) },
                )
            }
        }
    }
}

/**
 * «Достижения» — уже полученные пользователем значки (`profile/preference/badge/all/0`).
 *
 * В отличие от макета, где часть чипов нарисована тусклой как «не получено», API отдаёт только
 * коллекцию УЖЕ полученных бейджей (см. KDoc [Achievement]) — каталога всех возможных ачивок с
 * состоянием «заблокировано» сервер не даёт, поэтому здесь нет «тусклых» вариантов, только те,
 * что реально заработаны. Секция грузится отдельным запросом (см. `ProfileViewModel`) и просто не
 * показывается при пустом списке/ошибке — тем же паттерном, что [FavoriteGenresSection].
 */
@Composable
internal fun AchievementsSection(
    achievements: List<Achievement>,
    pinState: ProfilePinState,
    windowSize: AnixWindowSize,
    modifier: Modifier = Modifier,
) {
    if (achievements.isEmpty()) return

    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val isExpanded = windowSize == AnixWindowSize.Expanded
    val titleModifier = if (isExpanded) Modifier else Modifier.padding(horizontal = dimens.spaceM)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement =
            if (isExpanded) {
                Arrangement.spacedBy(ACHIEVEMENT_TITLE_GAP)
            } else {
                Arrangement.spacedBy(dimens.spaceS)
            },
    ) {
        if (isExpanded) {
            Text(
                text = strings.profileAchievementsTitle,
                modifier = titleModifier,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = ACHIEVEMENT_TITLE_FONT_SIZE),
                fontWeight = FontWeight.Bold,
            )
        } else {
            ProfileSectionTitleRow(
                title = strings.profileAchievementsTitle,
                section = ProfileShowcaseSection.ACHIEVEMENTS,
                pinState = pinState,
                modifier = titleModifier,
            )
        }

        if (isExpanded) {
            FlowRow(
                modifier = titleModifier,
                horizontalArrangement = Arrangement.spacedBy(ACHIEVEMENT_CHIP_GAP),
                verticalArrangement = Arrangement.spacedBy(ACHIEVEMENT_CHIP_GAP),
            ) {
                achievements.forEach { achievement ->
                    AchievementChip(label = achievement.name)
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
                contentPadding = PaddingValues(horizontal = dimens.spaceM),
            ) {
                items(items = achievements, key = { it.id }) { achievement ->
                    AchievementBadge(achievement = achievement)
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(achievement: Achievement) {
    val dimens = AnixThemeTokens.dimens

    Column(
        modifier = Modifier.width(ACHIEVEMENT_BADGE_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        AnixAsyncImage(
            model = achievement.badgeUrl,
            contentDescription = achievement.name,
            modifier =
                Modifier
                    .size(ACHIEVEMENT_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = achievement.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AchievementChip(label: String) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(ACHIEVEMENT_CHIP_RADIUS)
    // Сервер отдаёт только полученные бейджи (см. KDoc `Achievement`) — неполученных состояний
    // нет, параметр `selected` был мёртвым (ревью F12, 2026-09-17): активный стиль зашит.
    val containerColor = primary.copy(alpha = ACHIEVEMENT_SELECTED_CONTAINER_ALPHA)
    val borderColor = primary.copy(alpha = ACHIEVEMENT_SELECTED_BORDER_ALPHA)

    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(containerColor, shape)
                .border(BorderStroke(ACHIEVEMENT_CHIP_BORDER_WIDTH, borderColor), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(
                    horizontal = ACHIEVEMENT_CHIP_HORIZONTAL_PADDING,
                    vertical = ACHIEVEMENT_CHIP_VERTICAL_PADDING,
                ),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = ACHIEVEMENT_CHIP_FONT_SIZE,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val ACHIEVEMENT_BADGE_SIZE = 56.dp
private val ACHIEVEMENT_BADGE_WIDTH = 72.dp
private val ACHIEVEMENT_CHIP_RADIUS = 20.dp
private val ACHIEVEMENT_CHIP_BORDER_WIDTH = 1.dp
private val ACHIEVEMENT_CHIP_HORIZONTAL_PADDING = 16.dp
private val ACHIEVEMENT_CHIP_VERTICAL_PADDING = 9.dp
private val ACHIEVEMENT_CHIP_GAP = 8.dp
private val ACHIEVEMENT_TITLE_FONT_SIZE = 15.sp
private val ACHIEVEMENT_TITLE_GAP = 10.dp
private val ACHIEVEMENT_CHIP_FONT_SIZE = 12.5.sp
private const val ACHIEVEMENT_SELECTED_CONTAINER_ALPHA = 0.2f
private const val ACHIEVEMENT_SELECTED_BORDER_ALPHA = 0.5f

/**
 * Сетка статистики — Track A (точное соответствие макету): мокап рисует ровно 4 плитки в сетке
 * 2×2 (`padding 14/radius 14/фон w045/бордер w07`, значение 20px/800 Manrope + лейбл 11px t2-60).
 * Реальных полей у профиля восемь (watching/plan/completed/hold_on/dropped/favorite/friend/
 * comment) — ни одно не CUT-ано и не спрятано: убрать 4 из 8 значило бы решать судьбу данных
 * (продуктовое решение), а не только визуальную сверку, которой просит эта фаза. Вместо этого
 * сохранены все 8 плиток, но раскладка адаптируется: на телефоне остаётся 2 колонки (как в мокапе),
 * а на широком хром-маршруте количество колонок растёт, чтобы плитки не растягивались
 * до непропорционально больших карточек. Карточки получили точный стиль/типографику мокапа.
 *
 * Не через [StatTileRow]/[StatTile][com.aniko.ui.component.StatTile]: у того нет слотов под
 * кастомный контейнер (фон/бордер/radius) и типографику значения/лейбла из мокапа — расширять
 * публичный API общего компонента ради стиля одного экрана здесь не стали (тот же прецедент, что
 * `NewEpisodeCard` в `HomeScreen.kt` — см. её KDoc), а [StatTileData] как модель переиспользован.
 */
@Composable
internal fun StatsGrid(
    profile: ProfileDetails,
    windowSize: AnixWindowSize,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val tiles =
        listOf(
            StatTileData(profile.watchingCount.toString(), strings.listStatusWatching),
            StatTileData(profile.planCount.toString(), strings.listStatusPlanned),
            StatTileData(profile.completedCount.toString(), strings.listStatusCompleted),
            StatTileData(profile.holdOnCount.toString(), strings.listStatusOnHold),
            StatTileData(profile.droppedCount.toString(), strings.listStatusDropped),
            StatTileData(profile.favoriteCount.toString(), strings.libraryTabFavorites),
            StatTileData(profile.friendCount.toString(), strings.profileFriendsLabel),
            StatTileData(profile.commentCount.toString(), strings.profileCommentsLabel),
        )

    val isExpanded = windowSize == AnixWindowSize.Expanded
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = if (isExpanded) STAT_GRID_GAP_EXPANDED else dimens.spaceS
        val columns =
            if (isExpanded) {
                STAT_GRID_COLUMNS_EXPANDED
            } else {
                maxOf(MIN_STAT_GRID_COLUMNS, ceil((maxWidth + gap) / (STAT_CARD_MAX_WIDTH + gap)).toInt())
            }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowTiles.forEach { tile ->
                        ProfileStatCard(
                            tile = tile,
                            useExpandedStyle = isExpanded,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Последний ряд короче остальных (8 плиток не делится на 3/5 колонок) —
                    // заполняем пустыми весами, чтобы карточки не растягивались на всю ширину ряда
                    // (тот же приём, что в HomeQuickActions).
                    repeat(columns - rowTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Одна карточка сетки [StatsGrid] — см. её KDoc про причину не переиспользовать `StatTile`. */
@Composable
private fun ProfileStatCard(
    tile: StatTileData,
    useExpandedStyle: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AnixThemeTokens.colors
    val shape = RoundedCornerShape(if (useExpandedStyle) STAT_CARD_RADIUS_EXPANDED else PROFILE_CARD_RADIUS)
    val onClick = tile.onClick
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val padding = if (useExpandedStyle) STAT_CARD_PADDING_EXPANDED else PROFILE_CARD_PADDING
    val valueFontSize = if (useExpandedStyle) STAT_VALUE_FONT_SIZE_EXPANDED else STAT_VALUE_FONT_SIZE
    val labelFontSize = if (useExpandedStyle) STAT_LABEL_FONT_SIZE_EXPANDED else null

    Column(
        modifier =
            modifier
                .clip(shape)
                .background(colors.overlay045, shape)
                .border(BorderStroke(PROFILE_CARD_BORDER_WIDTH, colors.overlay07), shape)
                .then(clickModifier)
                .padding(padding),
        verticalArrangement =
            if (useExpandedStyle) {
                Arrangement.spacedBy(STAT_CARD_INNER_GAP_EXPANDED)
            } else {
                Arrangement.Top
            },
    ) {
        Text(
            text = tile.value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = valueFontSize),
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = tile.label,
            style =
                if (labelFontSize != null) {
                    MaterialTheme.typography.labelSmall.copy(fontSize = labelFontSize)
                } else {
                    MaterialTheme.typography.labelSmall
                },
            color = colors.textSecondary60,
        )
    }
}

/**
 * Два графика статистики (P9.T8 + P9.T9). Адаптив P9.T13: на Expanded — две колонки в одном
 * `Row` (иначе на широком экране получается длинная полупустая вертикальная лента), на
 * Compact/Medium — друг под другом.
 */
@Composable
internal fun ProfileChartsSection(
    profile: ProfileDetails,
    windowSize: AnixWindowSize,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    if (windowSize == AnixWindowSize.Expanded) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceL),
        ) {
            ListsDonutSection(profile = profile, windowSize = windowSize, modifier = Modifier.weight(1f))
            WeeklyActivitySection(profile = profile, modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
        ) {
            ListsDonutSection(profile = profile, windowSize = windowSize)
            WeeklyActivitySection(profile = profile)
        }
    }
}

/**
 * P9.T8: распределение по 5 спискам + легенда. Полностью пустой профиль (сумма нулевая)
 * обрабатывает сам [DonutChart] — рисует `chartNoData` вместо кольца.
 *
 * Track A (точное соответствие макету): карточка `w045`/`w07`/radius14/padding14 вокруг кольца
 * 88dp/13dp-обводка + легенда СПРАВА (не под кольцом, как раньше) с gap 18dp — заголовок секции
 * оставлен (в отличие от [StatsGrid] выше, у которого свой собственный заголовок «Watch Stats»,
 * здесь заголовок про сам донат-график; убирать его не стали, чтобы не менять видимый состав
 * Expanded-раскладки [ProfileChartsSection], которую эта фаза не трогает). Кольцо рисует
 * [DonutChart] без встроенной легенды (`legend = false`) — легенду строит сама секция, так как
 * мокап отделяет лейбл (`text-1` alpha 0.8) от счётчика (жирный, полная непрозрачность), а
 * встроенная легенда [DonutChart] красит всю строку одним стилем. «Вырез» в центре кольца —
 * не дырка в Canvas (тот и так рисует только Stroke-дугу, а не залитый сектор), а отдельный
 * закрашенный `bg-elevated` (`colorScheme.surface`) круг поверх неё через `centerContent` — так
 * его цвет ОТЛИЧАЕТСЯ от полупрозрачного фона самой карточки (`overlay045`), как в макете, а не
 * просвечивает его.
 */
@Suppress("LongMethod") // Карточка макета целиком (заголовок + Row кольцо/легенда + centerContent
// кольца) живёт одной функцией — разбиение на под-функции добавило бы косвенность ради счётчика
// строк (тот же приём, что и в остальных Track-A секциях этого файла).
@Composable
private fun ListsDonutSection(
    profile: ProfileDetails,
    windowSize: AnixWindowSize,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    val counts =
        listOf(
            strings.listStatusWatching to profile.watchingCount,
            strings.listStatusPlanned to profile.planCount,
            strings.listStatusCompleted to profile.completedCount,
            strings.listStatusOnHold to profile.holdOnCount,
            strings.listStatusDropped to profile.droppedCount,
        )
    val slices = counts.map { (name, count) -> ChartSlice(label = name, value = count.toFloat()) }
    val total = counts.sumOf { it.second }
    val palette = colors.chartSeries
    val isExpanded = windowSize == AnixWindowSize.Expanded
    val cardShape = RoundedCornerShape(if (isExpanded) DONUT_CARD_RADIUS_EXPANDED else PROFILE_CARD_RADIUS)
    val cardPadding = if (isExpanded) DONUT_CARD_PADDING_EXPANDED else PROFILE_CARD_PADDING
    val ringSize = if (isExpanded) DONUT_RING_SIZE_EXPANDED else DONUT_RING_SIZE
    val strokeWidth = if (isExpanded) DONUT_RING_STROKE_EXPANDED else DONUT_RING_STROKE
    val legendGap = if (isExpanded) DONUT_LEGEND_GAP_EXPANDED else DONUT_LEGEND_GAP
    val holeSize = ringSize - strokeWidth * 2

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = strings.profileListsChartTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(colors.overlay045, cardShape)
                    .border(BorderStroke(PROFILE_CARD_BORDER_WIDTH, colors.overlay07), cardShape)
                    .padding(cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(legendGap),
        ) {
            DonutChart(
                slices = slices,
                modifier = Modifier.size(ringSize),
                size = ringSize,
                strokeWidth = strokeWidth,
                legend = false,
                centerContent = {
                    Box(
                        modifier =
                            Modifier
                                .size(holeSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = total.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = strings.profileListsChartTotalLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary60,
                            )
                        }
                    }
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                counts.forEachIndexed { index, (name, count) ->
                    val color = palette[index % palette.size]
                    val swatchSize = if (isExpanded) LEGEND_SWATCH_SIZE_EXPANDED else LEGEND_SWATCH_SIZE
                    val labelStyle =
                        if (isExpanded) {
                            MaterialTheme.typography.bodySmall.copy(fontSize = LEGEND_LABEL_FONT_SIZE_EXPANDED)
                        } else {
                            MaterialTheme.typography.bodySmall
                        }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(swatchSize)
                                    .background(color, RoundedCornerShape(LEGEND_SWATCH_RADIUS)),
                        )
                        Text(
                            text = name,
                            style = labelStyle,
                            color =
                                if (isExpanded) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = LEGEND_LABEL_ALPHA)
                                },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = count.toString(),
                            style = labelStyle,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * P9.T9: недельный график активности из `watch_dynamics` — срез последних 7 точек по времени
 * (см. [ProfileDetails.recentWatchDynamics]). Подпись столбика — число месяца, как его отдаёт
 * сервер: локали это не касается, календарь пересчитывать не нужно.
 *
 * Track A (точное соответствие макету): столбики — accent/primary с альфой 0.8, скругление
 * сверху 5dp/снизу 2dp, ширина до 20dp, gap 7dp, высота контейнера 64dp. Все эти параметры уже
 * существовали или добавлены аддитивно в [WeeklyBarChart] (см. её KDoc) — сам компонент не
 * переписан, изменился только вызов.
 *
 * **2026-09-11 (живой баг-репорт пользователя, «белые полосы»/цветокор):** мокап рисовал
 * `trackColor = Color.Transparent` (без видимой "подложки" под столбиками) — на аккаунте с
 * низкой активностью за последние 7 дней [WeeklyBarChart.barHeightFractions] клэмпает почти все
 * столбики к минимальному 6%-огрызку (см. её KDoc), и на прозрачной подложке это читается как
 * пустая белая полоса, а не график — выглядит сломанным независимо от факта, что рендерится по
 * спецификации. Заменено на едва заметную заливку от `primary` (8% альфы) — область графика
 * теперь всегда читается как структура, даже при нулевой активности недели.
 */
@Composable
private fun WeeklyActivitySection(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val points = profile.recentWatchDynamics()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = strings.profileActivityTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (points.isEmpty()) {
            Text(text = strings.chartNoData, style = MaterialTheme.typography.bodyMedium)
        } else {
            WeeklyBarChart(
                entries = points.map { BarEntry(label = it.day.toString(), value = it.count.toFloat()) },
                barColor = MaterialTheme.colorScheme.primary.copy(alpha = ACTIVITY_BAR_ALPHA),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = ACTIVITY_TRACK_ALPHA),
                height = ACTIVITY_CHART_HEIGHT,
                barShape = ACTIVITY_BAR_SHAPE,
                gap = ACTIVITY_BAR_GAP,
                maxBarWidth = ACTIVITY_BAR_MAX_WIDTH,
            )
        }
    }
}

/**
 * P9.T11: «Недавно смотрели». Источник — `history[]` из того же ответа `profile/{id}` (5 последних
 * релизов), а не отдельный `GET history/{page}`: данные уже пришли, лишний сетевой запрос под
 * ленту из пяти карточек не нужен. Полноценная пагинируемая история живёт на экране «Библиотека»
 * (`LibraryRepository.historyPaginator`).
 *
 * Горизонтальные отступы навешивает сама секция (а не общий `Column` экрана) — лента обязана
 * скроллиться от края до края, как рельсы главного экрана.
 */
@Composable
internal fun RecentlyWatchedSection(
    profile: ProfileDetails,
    onReleaseClick: (Int) -> Unit,
    pinState: ProfilePinState,
    windowSize: AnixWindowSize,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    val edgePadding = if (windowSize == AnixWindowSize.Expanded) 0.dp else dimens.spaceM

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        ProfileSectionTitleRow(
            title = strings.profileRecentlyWatchedTitle,
            section = ProfileShowcaseSection.RECENTLY_WATCHED,
            pinState = pinState,
            modifier = Modifier.padding(horizontal = edgePadding),
        )
        if (profile.recentlyWatched.isEmpty()) {
            Text(
                text = strings.profileRecentlyWatchedEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary60,
                modifier = Modifier.padding(horizontal = edgePadding),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                contentPadding = PaddingValues(horizontal = edgePadding),
            ) {
                items(items = profile.recentlyWatched, key = { it.id }) { release ->
                    TitleCard(release = release, onClick = { onReleaseClick(release.id) })
                }
            }
        }
    }
}

// Track A (точное соответствие макету, 2026-09-04) — точные px/dp/alpha значения макета без
// готовых слотов в `AnixDimens`/`anixTypography` (тот же приём, что и `ACHIEVEMENT_BADGE_SIZE`
// выше/`AVATAR_SIZE` в `ProfileScreen.kt`).

/** [StatsGrid] — сетка карточек. */
private const val MIN_STAT_GRID_COLUMNS = 2
private const val STAT_GRID_COLUMNS_EXPANDED = 4
private val STAT_GRID_GAP_EXPANDED = 16.dp
private val PROFILE_CARD_RADIUS = 14.dp
private val PROFILE_CARD_PADDING = 14.dp
private val PROFILE_CARD_BORDER_WIDTH = 1.dp
private val STAT_VALUE_FONT_SIZE = 20.sp

/** Desktop-вариант [StatsGrid]: карточки крупнее, жирнее и плотнее к макету (строка 875). */
private val STAT_CARD_RADIUS_EXPANDED = 16.dp
private val STAT_CARD_PADDING_EXPANDED = 18.dp
private val STAT_VALUE_FONT_SIZE_EXPANDED = 26.sp
private val STAT_LABEL_FONT_SIZE_EXPANDED = 12.sp
private val STAT_CARD_INNER_GAP_EXPANDED = 6.dp

/** Максимальная комфортная ширина плитки статистики; при превышении добавляются колонки. */
private val STAT_CARD_MAX_WIDTH = 240.dp

/** [ListsDonutSection] — кольцо 88dp/13dp-обводка + легенда справа с gap 18dp. */
private val DONUT_RING_SIZE = 88.dp
private val DONUT_RING_STROKE = 13.dp
private val DONUT_LEGEND_GAP = 18.dp
private val LEGEND_SWATCH_SIZE = 8.dp
private val LEGEND_SWATCH_RADIUS = 3.dp
private const val LEGEND_LABEL_ALPHA = 0.8f

/** Desktop-вариант [ListsDonutSection]: кольцо 104dp/15dp, gap 24, легенда крупнее (строка 875). */
private val DONUT_CARD_RADIUS_EXPANDED = 16.dp
private val DONUT_CARD_PADDING_EXPANDED = 20.dp
private val DONUT_RING_SIZE_EXPANDED = 104.dp
private val DONUT_RING_STROKE_EXPANDED = 15.dp
private val DONUT_LEGEND_GAP_EXPANDED = 24.dp
private val LEGEND_SWATCH_SIZE_EXPANDED = 9.dp
private val LEGEND_LABEL_FONT_SIZE_EXPANDED = 12.5.sp

/** [WeeklyActivitySection] — accent-столбики альфа 0.8, скругление 5dp сверху/2dp снизу. */
private const val ACTIVITY_BAR_ALPHA = 0.8f

/** Едва заметная подложка под столбиками (2026-09-11, живой баг-репорт «белые полосы») — см.
 * KDoc [WeeklyActivitySection]: без неё низкоактивная неделя выглядит пустой белой полосой. */
private const val ACTIVITY_TRACK_ALPHA = 0.08f
private val ACTIVITY_CHART_HEIGHT = 64.dp
private val ACTIVITY_BAR_GAP = 7.dp
private val ACTIVITY_BAR_MAX_WIDTH = 20.dp
private val ACTIVITY_BAR_TOP_RADIUS = 5.dp
private val ACTIVITY_BAR_BOTTOM_RADIUS = 2.dp
private val ACTIVITY_BAR_SHAPE =
    RoundedCornerShape(
        topStart = ACTIVITY_BAR_TOP_RADIUS,
        topEnd = ACTIVITY_BAR_TOP_RADIUS,
        bottomStart = ACTIVITY_BAR_BOTTOM_RADIUS,
        bottomEnd = ACTIVITY_BAR_BOTTOM_RADIUS,
    )
