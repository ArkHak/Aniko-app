// TooManyFunctions: файл — набор мелких приватных presentation-хелперов шапки Title Detail
// (постер+инфо/watch-ряд/жанры/метаданные/скриншоты/info-строка + form-мапперы, плюс — Track A,
// design-match-remaining-screens, 2026-09-04 — отдельная hero-раскладка для phone Compact), само
// дробление на маленькие функции и есть способ держать `ReleaseHeaderSection` короткой (см. её же
// `LongMethod`-рефакторинг) — сливать их обратно ради счётчика было бы шагом назад.
@file:Suppress("TooManyFunctions")

package com.aniko.app.feature.release

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.ReleaseDetails
import com.aniko.model.ReleaseStatus
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixAsyncImage
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixPoster
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixDimens
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Шапка Title Detail (P7.T7, Трек C): постер, названия, метаданные, жанры, кнопка "Смотреть",
 * статус в списке/избранное, скриншоты, синопсис.
 *
 * Track A (design-match-remaining-screens, 2026-09-04): на phone Compact (см.
 * [com.aniko.ui.adaptive.AnixWindowSize]) точная разметка макета Claude Design (`showDetail`) —
 * обложка-hero 260dp с градиентом, круглая кнопка "назад" поверх неё, карточка контента внахлёст
 * (см. [CompactHeroHeader]) — заменяет прежнюю раскладку "постер+инфо в ряд". Desktop-проход
 * (2026-09-15): на Expanded — [ExpandedDrawerHeader] (hero 230dp правого ящика 520dp мокапа
 * `showDetail`, кнопка "назад" тоже на обложке, `TopAppBar` экрана на этом размере отключён).
 * На Medium рисуется прежняя [WideHeaderLayout] без изменений.
 *
 * [details] может быть `null` (ещё грузится или упала независимо от базового [release], см. D1 в
 * KDoc [ReleaseDetailsUiState]) — секции, целиком зависящие от неё (расширенные метаданные,
 * скриншоты), в этом случае просто не рисуются, а не блокируют всю шапку.
 *
 * Легальные стриминг-площадки (сверено вживую 2026-09-23, см. KDoc `ReleaseDto`/`ReleaseDetails`):
 * [details]`.note`, если не пусто, рисуется как короткий информационный баннер ([ReleaseNoteBanner])
 * во всех трёх раскладках. Кнопка "Смотреть" ([HeroPlayButton]) скрывается, когда
 * [details]`.isThirdPartyPlatformsDisabled == true` — сервер в этом случае просит опираться на
 * `ReleaseStreamingPlatformsSection` вместо обычного флоу выбора источника (см.
 * `ReleaseDetailsScreen.ReleaseDetailsContent`, где скрывается и сама `ReleaseEpisodesSection`).
 */
@Suppress("LongParameterList") // Публичная сигнатура шапки: 9 обязательных колбэков/данных —
// 8 исходных (см. историю коммитов) + [onBackClick] (Track A, компактная hero-обложка сама
// рисует кнопку "назад" вместо `TopAppBar`, см. KDoc [ReleaseDetailsScreen]), группировка в
// конфиг-класс добавила бы косвенность ради обхода линта, а не ради читаемости вызывающего кода.
@Composable
fun ReleaseHeaderSection(
    release: Release,
    details: ReleaseDetails?,
    detailsError: LoadError?,
    isResolvingPlay: Boolean,
    onWatchClick: () -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryDetails: () -> Unit,
    onShareClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (LocalAnixWindowSize.current) {
        AnixWindowSize.Compact ->
            CompactHeroHeader(
                release = release,
                details = details,
                detailsError = detailsError,
                isResolvingPlay = isResolvingPlay,
                onWatchClick = onWatchClick,
                onChangeListStatus = onChangeListStatus,
                onToggleFavorite = onToggleFavorite,
                onRetryDetails = onRetryDetails,
                onShareClick = onShareClick,
                onBackClick = onBackClick,
                modifier = modifier,
            )

        AnixWindowSize.Expanded ->
            ExpandedDrawerHeader(
                release = release,
                details = details,
                detailsError = detailsError,
                isResolvingPlay = isResolvingPlay,
                onWatchClick = onWatchClick,
                onChangeListStatus = onChangeListStatus,
                onToggleFavorite = onToggleFavorite,
                onRetryDetails = onRetryDetails,
                onShareClick = onShareClick,
                onBackClick = onBackClick,
                modifier = modifier,
            )

        AnixWindowSize.Medium ->
            WideHeaderLayout(
                release = release,
                details = details,
                detailsError = detailsError,
                isResolvingPlay = isResolvingPlay,
                onWatchClick = onWatchClick,
                onChangeListStatus = onChangeListStatus,
                onToggleFavorite = onToggleFavorite,
                onRetryDetails = onRetryDetails,
                onShareClick = onShareClick,
                modifier = modifier,
            )
    }
}

// ============================================================================================
// Medium (P13.T13/P5.T3 — раскладка зафиксирована, не трогаем): прежняя структура, но
// цвета/типографика/кнопки/чипы ретокенизированы под те же конвенции, что и phone Compact.
// ============================================================================================

@Suppress("LongParameterList") // См. обоснование в [ReleaseHeaderSection] — тот же набор данных
// минус [onBackClick] (эта раскладка не рисует свою кнопку "назад", ей занимается `TopAppBar`).
@Composable
private fun WideHeaderLayout(
    release: Release,
    details: ReleaseDetails?,
    detailsError: LoadError?,
    isResolvingPlay: Boolean,
    onWatchClick: () -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryDetails: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        PosterAndInfoRow(release = release, details = details, dimens = dimens, strings = strings)

        WatchAndFavoriteRow(
            release = release,
            isResolvingPlay = isResolvingPlay,
            onWatchClick = onWatchClick,
            onChangeListStatus = onChangeListStatus,
            onToggleFavorite = onToggleFavorite,
            onShareClick = onShareClick,
            hideWatchAction = details?.isThirdPartyPlatformsDisabled == true,
        )

        if (release.genres.isNotEmpty()) {
            GenreChipRow(genres = release.genres)
        }

        MetadataSection(details = details)

        if (detailsError != null && details == null) {
            AnixErrorState(
                message = detailsError.toDetailsMessage(strings),
                onRetry = onRetryDetails,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ReleaseNoteBanner(details = details)

        val screenshots = details?.screenshotUrls.orEmpty()
        if (screenshots.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                HeroSectionTitle(text = strings.titleDetailScreenshots)
                ScreenshotRail(urls = screenshots)
            }
        }

        val description = release.description
        if (!description.isNullOrBlank()) {
            HeroSynopsis(text = description, strings = strings)
        }
    }
}

/** Постер + название/альт.названия/базовые info-строки — вынесено из [WideHeaderLayout]
 *  отдельной функцией (detekt `LongMethod`). */
@Composable
private fun PosterAndInfoRow(
    release: Release,
    details: ReleaseDetails?,
    dimens: AnixDimens,
    strings: Strings,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        AnixPoster(url = release.posterUrl, contentDescription = release.title)

        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
            Text(
                text = release.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val originalTitle = release.originalTitle
            if (!originalTitle.isNullOrBlank() && originalTitle != release.title) {
                Text(
                    text = originalTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnixThemeTokens.colors.textSecondary60,
                )
            }
            val titleAlt = details?.titleAlt
            if (!titleAlt.isNullOrBlank() && titleAlt != release.title && titleAlt != originalTitle) {
                Text(
                    text = titleAlt,
                    style = MaterialTheme.typography.bodySmall,
                    color = AnixThemeTokens.colors.textSecondary60,
                )
            }

            InfoRow(label = strings.releaseInfoYear, value = release.year?.toString())
            InfoRow(label = strings.releaseInfoStatus, value = release.status.toDisplayName(strings))
            InfoRow(label = strings.releaseInfoEpisodesLabel, value = release.episodesLabel())
            InfoRow(label = strings.releaseInfoRating, value = release.grade?.let { formatGrade(it) })
        }
    }
}

/**
 * Кнопка "Смотреть" + тоггл избранного/статуса списка/поделиться.
 * Раскладка остаётся прежней (кнопка "Смотреть" над рядом избранное/поделиться/статус),
 * но каждый элемент ретокенизирован под те же конвенции, что и [CompactHeroHeader]
 * ([HeroPlayButton], [HeroAddToListButton], избранное/поделиться с теми же tint/filled).
 */
@Suppress("LongParameterList") // 7 параметров: 6 независимых интерактивных зон (плей/статус/
// избранное/поделиться) + [hideWatchAction] (легальные стриминг-площадки, см. KDoc
// [ReleaseHeaderSection]) — та же причина, что у `WideHeaderLayout` выше.
@Composable
private fun WatchAndFavoriteRow(
    release: Release,
    isResolvingPlay: Boolean,
    onWatchClick: () -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: () -> Unit,
    hideWatchAction: Boolean = false,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        if (!hideWatchAction) {
            HeroPlayButton(
                isResolvingPlay = isResolvingPlay,
                onClick = onWatchClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            val favoriteDescription =
                if (release.isFavorite) strings.commonRemoveFromFavorites else strings.commonAddToFavorites
            // Подтверждено на устройстве (Фаза 11, T9): IconButton не сливает
            // Icon.contentDescription в свой кликабельный узел.
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.clearAndSetSemantics { contentDescription = favoriteDescription },
            ) {
                AnixIcon(
                    name = "favorite",
                    contentDescription = null,
                    filled = release.isFavorite,
                    tint =
                        if (release.isFavorite) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.clearAndSetSemantics { contentDescription = strings.shareButtonContentDescription },
            ) {
                AnixIcon(name = "share", contentDescription = null, filled = true)
            }
            HeroAddToListButton(release = release, onChangeListStatus = onChangeListStatus)
        }
    }
}

/**
 * Жанры отдельными нередактируемыми чипами (не строка через запятую) — `Release.genres` уже
 * распарсен в список маппером (`ReleaseMapper.toDomain`), здесь только отрисовка.
 *
 * Phase-15 accent split: жанровые чипы — primary-акцент (`AnixFilterChipRow` с `selectedColor =
 * primary` в Catalog), статусные — secondary. Чипы read-only, поэтому рисуются как неинтерактивные
 * Surface-пилюли с той же заливкой/бордером, что у выбранного чипа `AnixFilterChipRow`.
 */
@Composable
private fun GenreChipRow(genres: List<String>) {
    val dimens = AnixThemeTokens.dimens
    val shape = RoundedCornerShape(dimens.cornerPill)
    val accent = MaterialTheme.colorScheme.primary

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        genres.forEach { genre ->
            Surface(
                color = accent.copy(alpha = GENRE_CHIP_CONTAINER_ALPHA),
                shape = shape,
                border = BorderStroke(GENRE_CHIP_BORDER_WIDTH, accent.copy(alpha = GENRE_CHIP_BORDER_ALPHA)),
            ) {
                Text(
                    text = genre,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = GENRE_CHIP_FONT_SIZE,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = dimens.spaceS, vertical = dimens.spaceXs),
                )
            }
        }
    }
}

/**
 * Расширенные метаданные (P7.T7) — только поля из [ReleaseDetails], каждое опционально.
 *
 * Редизайн 2026-09-18 (живой фидбек пользователя, «список выглядит скучно»): вместо строк
 * «Метка: значение» каждый пункт — [InfoRow]-плитка с тематическим эмодзи в скруглённой
 * подложке, меткой-капшном и значением полужирным. Эмодзи — часть оформления (как иконка), а
 * не локализуемый текст, поэтому задаются прямо здесь, а не в `Strings`.
 */
@Composable
private fun MetadataSection(details: ReleaseDetails?) {
    if (details == null) return
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        InfoRow(emoji = "🎬", label = strings.titleDetailStudio, value = details.studio)
        InfoRow(emoji = "🌍", label = strings.titleDetailCountry, value = details.country)
        InfoRow(emoji = "✍️", label = strings.titleDetailAuthor, value = details.author)
        InfoRow(emoji = "🎥", label = strings.titleDetailDirector, value = details.director)
        InfoRow(emoji = "📅", label = strings.titleDetailSeason, value = details.season)
        InfoRow(emoji = "🗓️", label = strings.titleDetailReleaseDate, value = details.releaseDate)
        InfoRow(emoji = "🔞", label = strings.titleDetailAgeRating, value = details.ageRating)
        InfoRow(emoji = "⏱️", label = strings.titleDetailEpisodeDuration, value = details.duration?.toString())
        InfoRow(emoji = "📺", label = strings.titleDetailCategory, value = details.category)
        InfoRow(emoji = "📖", label = strings.titleDetailSource, value = details.source)
        InfoRow(emoji = "🎙️", label = strings.titleDetailTranslators, value = details.translators)
    }
}

/**
 * Информационный баннер `ReleaseDetails.note` (сверено вживую 2026-09-23, см. KDoc `ReleaseDto`
 * в `shared/data`) — простой текст, HTML не парсится (в живом сэмпле — одно предложение:
 * «Данный материал лицензирован на территории вашей страны.»). Ничего не рисует, если [details]
 * `null` или `note` пуст.
 *
 * Цвета баннера — опциональные hex-строки с сервера ([ReleaseDetails.noteBackgroundColorLight]/
 * `*Dark`, [ReleaseDetails.noteTextColorLight]/`*Dark`); во всех живых сэмплах на 2026-09-23 они
 * были `null` — в этом случае баннер использует обычный токен-стиль приложения (`overlay045`/
 * `textSecondary75`, тот же, что у `ReleaseCommentPreviewRow`), а не какой-то один жёстко
 * захардкоженный цвет. `parseHexColorOrNull` — best-effort: невалидная строка тихо игнорируется,
 * а не роняет экран. Светлая/тёмная пара выбирается тем же приёмом luminance-порога, что уже
 * используется в проекте для аналогичного theme-aware цвета вне `ThemeStore` (см.
 * `com.aniko.ui.adaptive.SidebarSlot.sidebarBackgroundColor`) — не `isSystemInDarkTheme()`, тема
 * приложения выбирается явно, системную не следует.
 */
@Composable
private fun ReleaseNoteBanner(details: ReleaseDetails?) {
    val note = details?.note
    if (note.isNullOrBlank()) return
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val isDark = MaterialTheme.colorScheme.surface.luminance() < NOTE_BANNER_DARK_LUMINANCE_THRESHOLD
    val backgroundColor =
        (if (isDark) details.noteBackgroundColorDark else details.noteBackgroundColorLight)
            ?.let(::parseHexColorOrNull)
            ?: colors.overlay045
    val textColor =
        (if (isDark) details.noteTextColorDark else details.noteTextColorLight)
            ?.let(::parseHexColorOrNull)
            ?: colors.textSecondary75
    val shape = RoundedCornerShape(dimens.cornerM)

    Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = textColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(backgroundColor, shape)
                .padding(dimens.space12),
    )
}

/**
 * Best-effort парсинг `#RRGGBB`/`#AARRGGBB` в [Color] — `null` на любой некорректный ввод
 * (длина/не-hex символы), парсинг никогда не бросает исключение. См. KDoc [ReleaseNoteBanner].
 */
private fun parseHexColorOrNull(hex: String): Color? {
    val cleaned = hex.removePrefix("#")
    val argbHex =
        when (cleaned.length) {
            HEX_COLOR_LENGTH_RGB -> "FF$cleaned"
            HEX_COLOR_LENGTH_ARGB -> cleaned
            else -> null
        } ?: return null
    return argbHex.toLongOrNull(radix = HEX_RADIX)?.let { Color(it.toInt()) }
}

@Composable
private fun ScreenshotRail(urls: List<String>) {
    val dimens = AnixThemeTokens.dimens
    val shape = RoundedCornerShape(dimens.cornerM)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        items(items = urls, key = { it }) { url ->
            AnixAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(SCREENSHOT_WIDTH)
                        .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape),
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
    emoji: String? = null,
) {
    if (value.isNullOrBlank()) return
    val dimens = AnixThemeTokens.dimens

    // Без эмодзи (emoji == null) — прежний компактный вариант «Метка: значение» одной строкой:
    // он остаётся в шапке рядом с постером ([WideHeaderLayout]), где плитка с подложкой была бы
    // избыточной. Плиточный вид с эмодзи — у секции расширенных метаданных (см. [MetadataSection]).
    if (emoji == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.bodySmall,
                color = AnixThemeTokens.colors.textSecondary60,
            )
            Text(text = value, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        // Эмодзи-«иконка» пункта на скруглённой подложке — визуальный якорь строки (см. KDoc
        // [MetadataSection]). Эмодзи декоративный, поэтому без contentDescription: смысл строки
        // целиком несут метка и значение рядом.
        Box(
            modifier =
                Modifier
                    .size(INFO_ROW_EMOJI_BOX_SIZE)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(dimens.cornerM)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AnixThemeTokens.colors.textSecondary60,
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ============================================================================================
// Phone Compact (Track A, design-match-remaining-screens, 2026-09-04) — hero-обложка + карточка
// внахлёст под точную разметку макета Claude Design (`showDetail`).
// ============================================================================================

/**
 * Hero-раскладка шапки Title Detail на phone Compact: обложка 260dp с градиентом в bg-elevated
 * снизу, круглая кнопка "назад" поверх неё, карточка контента с отступом -30dp (внахлёст на
 * обложку). Обложка и карточка живут в одном [Box] (не в [Column] с `Modifier.offset`) —
 * `offset` сдвигает элемент только визуально, не уменьшая занимаемое им место в layout, из-за
 * чего между карточкой и следующей секцией остался бы пустой зазор высотой с нахлёст; [Box] с
 * `padding(top = ...)` у карточки взамен `offset` даёт родителю верную суммарную высоту.
 */
@Suppress("LongParameterList", "LongMethod") // См. обоснование в [ReleaseHeaderSection].
@Composable
private fun CompactHeroHeader(
    release: Release,
    details: ReleaseDetails?,
    detailsError: LoadError?,
    isResolvingPlay: Boolean,
    onWatchClick: () -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryDetails: () -> Unit,
    onShareClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val bgElevated = MaterialTheme.colorScheme.surface // bg-elevated текущей темы (см. Color.kt)

    Box(modifier = modifier.fillMaxWidth()) {
        AnixAsyncImage(
            model = release.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(HERO_COVER_HEIGHT).align(Alignment.TopCenter),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HERO_COVER_HEIGHT)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = HERO_GRADIENT_TOP_ALPHA),
                                    bgElevated.copy(alpha = HERO_GRADIENT_BOTTOM_ALPHA),
                                ),
                        ),
                    ),
        )
        HeroBackButton(
            onClick = onBackClick,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.spaceM, top = dimens.spaceM),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = HERO_COVER_HEIGHT - HERO_CARD_OVERLAP)
                    .clip(RoundedCornerShape(topStart = dimens.cornerL, topEnd = dimens.cornerL))
                    .background(bgElevated)
                    .padding(horizontal = HERO_CARD_HORIZONTAL_PADDING, vertical = dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            Text(
                text = release.title,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = HERO_TITLE_SIZE,
                        fontWeight = FontWeight.ExtraBold,
                    ),
            )

            HeroMetaRow(release = release, details = details)
            HeroRatingGenresRow(release = release)

            HeroActionsRow(
                release = release,
                isResolvingPlay = isResolvingPlay,
                onWatchClick = onWatchClick,
                onChangeListStatus = onChangeListStatus,
                onToggleFavorite = onToggleFavorite,
                onShareClick = onShareClick,
                hideWatchAction = details?.isThirdPartyPlatformsDisabled == true,
            )

            if (detailsError != null && details == null) {
                AnixErrorState(
                    message = detailsError.toDetailsMessage(strings),
                    onRetry = onRetryDetails,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ReleaseNoteBanner(details = details)

            val description = release.description
            if (!description.isNullOrBlank()) {
                HeroSynopsis(text = description, strings = strings)
            }

            // Расширенные метаданные/скриншоты вне точной разметки макета для phone Compact
            // (мокап `showDetail` их не показывает вовсе), но данные реальны и уже загружены —
            // молча терять их было бы регрессией функциональности ради пиксель-точности внешнего
            // вида, которую бриф не требовал ("НЕ переписывай data-flow"). Оставлены под
            // синопсисом тем же визуальным языком, что и раньше.
            MetadataSection(details = details)

            val screenshots = details?.screenshotUrls.orEmpty()
            if (screenshots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                    HeroSectionTitle(text = strings.titleDetailScreenshots)
                    ScreenshotRail(urls = screenshots)
                }
            }
        }
    }
}

// ============================================================================================
// Desktop Expanded (desktop-проход 2026-09-15, мокап `showDetail`, правый ящик 520px) — hero
// 230dp + контент с padding 0/24/30 и нахлёстом -20dp на обложку, кнопка "назад" на обложке.
// ============================================================================================

/**
 * Шапка Title Detail в правом ящике на Expanded: обложка 230dp с градиентным скримом в
 * bg-elevated (96%), круглая кнопка "назад" 34dp (`rgba(0,0,0,.5)`, top/left 16), контент —
 * колонка с gap 16 и горизонтальным padding 24dp, заезжающая на обложку на 20dp. В отличие от
 * [CompactHeroHeader] отдельной скруглённой карточки с собственным фоном нет — фон ящика
 * (`MaterialTheme.colorScheme.surface`, рисует `ListDetailHost`) уже и есть bg-elevated, вторая
 * плашка поверх была бы заметна. Тот же приём [Box] + `padding(top = ...)` вместо `offset`, что
 * и у [CompactHeroHeader] (см. её KDoc), — чтобы родитель получил верную суммарную высоту.
 */
@Suppress("LongParameterList", "LongMethod") // См. обоснование в [ReleaseHeaderSection].
@Composable
private fun ExpandedDrawerHeader(
    release: Release,
    details: ReleaseDetails?,
    detailsError: LoadError?,
    isResolvingPlay: Boolean,
    onWatchClick: () -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryDetails: () -> Unit,
    onShareClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val bgElevated = MaterialTheme.colorScheme.surface // bg-elevated текущей темы (см. Color.kt)

    Box(modifier = modifier.fillMaxWidth()) {
        AnixAsyncImage(
            model = release.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(DRAWER_COVER_HEIGHT).align(Alignment.TopCenter),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(DRAWER_COVER_HEIGHT)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = HERO_GRADIENT_TOP_ALPHA),
                                    bgElevated.copy(alpha = HERO_GRADIENT_BOTTOM_ALPHA),
                                ),
                        ),
                    ),
        )
        HeroBackButton(
            onClick = onBackClick,
            size = DRAWER_BACK_BUTTON_SIZE,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.spaceM, top = dimens.spaceM),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = DRAWER_COVER_HEIGHT - DRAWER_CONTENT_OVERLAP)
                    .padding(horizontal = DRAWER_CONTENT_PADDING),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
        ) {
            Text(
                text = release.title,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = HERO_TITLE_SIZE,
                        fontWeight = FontWeight.ExtraBold,
                    ),
            )

            HeroMetaRow(release = release, details = details)
            HeroRatingGenresRow(release = release)

            HeroActionsRow(
                release = release,
                isResolvingPlay = isResolvingPlay,
                onWatchClick = onWatchClick,
                onChangeListStatus = onChangeListStatus,
                onToggleFavorite = onToggleFavorite,
                onShareClick = onShareClick,
                buttonHeight = DRAWER_BUTTON_HEIGHT,
                buttonRadius = DRAWER_BUTTON_RADIUS,
                hideWatchAction = details?.isThirdPartyPlatformsDisabled == true,
            )

            if (detailsError != null && details == null) {
                AnixErrorState(
                    message = detailsError.toDetailsMessage(strings),
                    onRetry = onRetryDetails,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ReleaseNoteBanner(details = details)

            val description = release.description
            if (!description.isNullOrBlank()) {
                HeroSynopsis(text = description, strings = strings)
            }

            // Как и в Compact-hero (см. комментарий там): данные реальны и уже загружены, молча
            // терять их ради пиксель-точности нельзя — оставлены под синопсисом тем же
            // визуальным языком, что и раньше.
            MetadataSection(details = details)

            val screenshots = details?.screenshotUrls.orEmpty()
            if (screenshots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                    HeroSectionTitle(text = strings.titleDetailScreenshots)
                    ScreenshotRail(urls = screenshots)
                }
            }
        }
    }
}

/** Круглая кнопка "назад" поверх hero-обложки — `rgba(0,0,0,0.5)` без backdrop-blur: обычный
 *  `Modifier.blur` блюрит только СВОИХ детей, не то, что находится позади композабла — блюр
 *  ФОНА (backdrop filter) в стабильном Compose Multiplatform этой версии не даёт кросс-платформенного
 *  примитива (см. похожее решение НЕ использовать `Modifier.blur` в `CommentRow.kt`/`CommentMessage`,
 *  хоть и по другой причине — там это блокирующая контент функциональность, здесь чисто
 *  декоративный эффект, деградация до просто полупрозрачного круга не теряет ни читаемость, ни
 *  тач-таргет). */
@Composable
private fun HeroBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = HERO_BACK_BUTTON_SIZE,
) {
    val strings = LocalStrings.current
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = HERO_BACK_BUTTON_SCRIM_ALPHA))
                .clickable(onClickLabel = strings.backContentDescription, role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = strings.backContentDescription
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        AnixIcon(name = "arrow_back", contentDescription = null, tint = Color.White)
    }
}

/** "год · тип · серии · студия" — `t2-60`, 12sp (макет). */
@Composable
private fun HeroMetaRow(
    release: Release,
    details: ReleaseDetails?,
) {
    val strings = LocalStrings.current
    val parts =
        listOfNotNull(
            release.year?.toString(),
            release.status.toDisplayName(strings),
            release.episodesLabel(),
            details?.studio?.takeIf { it.isNotBlank() },
        )
    if (parts.isEmpty()) return

    Text(
        text = parts.joinToString(HERO_META_SEPARATOR),
        style = MaterialTheme.typography.bodySmall.copy(color = AnixThemeTokens.colors.textSecondary60),
    )
}

/** "★ 8.7" золотым (`gold` = [AnixThemeTokens.colors.warning]) 13px/700 + жанры `t2-60` 11px. */
@Composable
private fun HeroRatingGenresRow(release: Release) {
    val dimens = AnixThemeTokens.dimens
    val grade = release.grade
    if (grade == null && release.genres.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        if (grade != null) {
            AnixIcon(
                name = "star",
                contentDescription = null,
                filled = true,
                tint = AnixThemeTokens.colors.warning,
                modifier = Modifier.size(HERO_RATING_STAR_SIZE),
            )
            Text(
                text = formatGrade(grade),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = HERO_RATING_TEXT_SIZE,
                        fontWeight = FontWeight.Bold,
                        color = AnixThemeTokens.colors.warning,
                    ),
            )
        }
        if (release.genres.isNotEmpty()) {
            Text(
                text = release.genres.joinToString(HERO_GENRE_SEPARATOR),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = HERO_GENRE_TEXT_SIZE,
                        color = AnixThemeTokens.colors.textSecondary60,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * Play (flex 1, accent, 46dp/radius12) + "Add to list" (см. [HeroAddToListButton]) в одном ряду,
 * избранное/поделиться — отдельным рядом ниже. Мокап рисует ровно ДВЕ кнопки в этом ряду
 * ("Play"/"Add to list"), но существующий функционал экрана богаче — раздельные избранное/
 * поделиться/выбор ТОЧНОГО статуса из 5 вариантов — сознательно не срезан до одной кнопки ради
 * пиксель-точности ("НЕ переписывай data-flow" в брифе): вместо этого та же функциональность
 * выбора статуса переехала под ту же самую "Add to list"-кнопку (выпадающее меню,
 * [HeroAddToListButton]) — визуально совпадает с макетом, семантика колбэков не поменялась.
 * На Medium/Expanded [WatchAndFavoriteRow] сохраняет прежнюю структуру (кнопка "Смотреть" над
 * рядом избранное/поделиться/статус), но использует те же [HeroPlayButton]/[HeroAddToListButton]
 * и tint иконок, что и Compact-hero. Избранное/поделиться остались отдельными иконками — макет
 * их на этом экране вовсе не показывает, но убирать реальную функциональность не входит в задачу
 * "визуальной сверки".
 */
@Suppress("LongParameterList") // Координирующий блок: [release] + 5 колбэков, ровно по числу
// независимых интерактивных зон (плей/статус/избранное/поделиться), + [hideWatchAction]
// (легальные стриминг-площадки, см. KDoc [ReleaseHeaderSection]) — тот же паттерн и то же
// обоснование, что и у `WatchAndFavoriteRow` выше в этом файле.
@Composable
private fun HeroActionsRow(
    release: Release,
    isResolvingPlay: Boolean,
    onWatchClick: () -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: () -> Unit,
    buttonHeight: Dp = HERO_BUTTON_HEIGHT,
    buttonRadius: Dp = Dp.Unspecified,
    hideWatchAction: Boolean = false,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
            if (!hideWatchAction) {
                HeroPlayButton(
                    isResolvingPlay = isResolvingPlay,
                    onClick = onWatchClick,
                    modifier = Modifier.weight(1f),
                    height = buttonHeight,
                    cornerRadius = buttonRadius,
                )
            }
            HeroAddToListButton(
                release = release,
                onChangeListStatus = onChangeListStatus,
                height = buttonHeight,
                cornerRadius = buttonRadius,
                // Play — единственный weight(1f) в ряду обычно, поэтому один растягивает ряд;
                // когда его нет (hideWatchAction), эта кнопка остаётся единственной и должна сама
                // забрать вес (modifier — на её внешний Box, прямой ребёнок этого Row) и
                // растянуть свой видимый/кликабельный Row на всю ширину (fillWidth — см. её KDoc).
                modifier = if (hideWatchAction) Modifier.weight(1f) else Modifier,
                fillWidth = hideWatchAction,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        ) {
            val favoriteDescription =
                if (release.isFavorite) strings.commonRemoveFromFavorites else strings.commonAddToFavorites
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.clearAndSetSemantics { contentDescription = favoriteDescription },
            ) {
                AnixIcon(
                    name = "favorite",
                    contentDescription = null,
                    filled = release.isFavorite,
                    tint =
                        if (release.isFavorite) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.clearAndSetSemantics { contentDescription = strings.shareButtonContentDescription },
            ) {
                AnixIcon(name = "share", contentDescription = null, filled = true)
            }
        }
    }
}

/**
 * Кнопка "Смотреть" точно под макет: flex1 (задаётся вызывающей стороной через [modifier]),
 * высота 46dp, radius 12dp (= [AnixDimens.cornerM], уже 12dp — тот же токен, без нового), белый
 * треугольник play + текст 14px/700 Manrope, фон — `MaterialTheme.colorScheme.primary`.
 *
 * Проверено (Track A, `Color.kt`): `colorScheme.primary` в [com.aniko.ui.theme.AnixDarkColors]/
 * [com.aniko.ui.theme.AnixLightColors] — это буквально `AnixPalette.PrimaryDark`/`PrimaryLight`,
 * то есть accent-токен макета один в один, без расхождения — отдельный alias под именем "accent"
 * в [AnixThemeTokens.colors] не заводился (не нужен, значения совпадают), поэтому здесь
 * используется `colorScheme.primary` напрямую, а не что-то из `AnixThemeTokens.colors`.
 */
@Composable
private fun HeroPlayButton(
    isResolvingPlay: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = HERO_BUTTON_HEIGHT,
    cornerRadius: Dp = Dp.Unspecified,
) {
    val strings = LocalStrings.current
    val shape =
        RoundedCornerShape(
            if (cornerRadius == Dp.Unspecified) AnixThemeTokens.dimens.cornerM else cornerRadius,
        )

    Row(
        modifier =
            modifier
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary, shape)
                .clickable(
                    enabled = !isResolvingPlay,
                    onClickLabel = strings.titleDetailWatch,
                    role = Role.Button,
                    onClick = onClick,
                ).clearAndSetSemantics {
                    contentDescription = strings.titleDetailWatch
                    role = Role.Button
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isResolvingPlay) {
            CircularProgressIndicator(
                modifier = Modifier.size(HERO_PLAY_ICON_SIZE),
                color = Color.White,
                strokeWidth = PLAY_SPINNER_STROKE,
            )
        } else {
            AnixIcon(
                name = "play_arrow",
                contentDescription = null,
                filled = true,
                tint = Color.White,
                modifier = Modifier.size(HERO_PLAY_ICON_SIZE),
            )
        }
        Text(
            text = strings.titleDetailWatch,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = HERO_BUTTON_TEXT_SIZE,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            modifier = Modifier.padding(start = AnixThemeTokens.dimens.spaceXs),
        )
    }
}

/**
 * "Add to list": высота 46dp, padding 0/16, radius 12dp, фон `overlay07`, бордер `overlay10`,
 * текст 13px/600 — точно под макет. Подпись — текущий статус ([Release.myListStatus]) или
 * [Strings.titleDetailAddToList], если релиз ещё не в списке; тап открывает [DropdownMenu] со
 * всеми [ListStatus] (тот же toggle-колбэк, что был у `ChipRow` в `WatchAndFavoriteRow` — повторный
 * выбор уже активного статуса снимает его).
 */
@Composable
private fun HeroAddToListButton(
    release: Release,
    onChangeListStatus: (ListStatus?) -> Unit,
    height: Dp = HERO_BUTTON_HEIGHT,
    cornerRadius: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier,
    // [modifier] (обычно `weight(1f)`) достаётся внешнему `Box` — это прямой ребёнок родительского
    // `Row`, только к нему `RowScope.weight` вообще применим. Но сама кнопка (фон/рамка/клик/
    // семантика) — вложенный `Row`, и он этот вес сам по себе не наследует: `Box` с точными
    // constraints от `weight` не растягивает контент по умолчанию (`contentAlignment = TopStart`),
    // поэтому без отдельного флага внутренний `Row` остаётся intrinsic-ширины, а расширяется
    // только невидимый `Box` вокруг него. [fillWidth] явно прокидывает `fillMaxWidth()` внутрь,
    // когда эта кнопка — единственный элемент ряда (`hideWatchAction` в `HeroActionsRow`); в обычном
    // случае (`false`, дефолт) поведение не меняется вообще.
    fillWidth: Boolean = false,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (cornerRadius == Dp.Unspecified) dimens.cornerM else cornerRadius)
    val label = release.myListStatus?.displayName(strings) ?: strings.titleDetailAddToList

    Box(modifier = modifier) {
        Row(
            modifier =
                (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .height(height)
                    .clip(shape)
                    .background(colors.overlay07, shape)
                    .border(BorderStroke(HERO_ADD_BUTTON_BORDER_WIDTH, colors.overlay10), shape)
                    .clickable(onClickLabel = label, role = Role.Button) { expanded = true }
                    .clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                    }.padding(horizontal = dimens.spaceM),
            horizontalArrangement = if (fillWidth) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = HERO_ADD_BUTTON_TEXT_SIZE,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ListStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(text = status.displayName(strings)) },
                    onClick = {
                        expanded = false
                        onChangeListStatus(if (status == release.myListStatus) null else status)
                    },
                )
            }
        }
    }
}

/** Заголовок "Synopsis"/"Описание" (14px/700) + текст 13px, line-height ×1.55, `textSecondary72`. */
@Composable
private fun HeroSynopsis(
    text: String,
    strings: Strings,
) {
    val dimens = AnixThemeTokens.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        HeroSectionTitle(text = strings.titleDetailSynopsis)
        Text(
            text = text,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontSize = HERO_SYNOPSIS_TEXT_SIZE,
                    lineHeight = HERO_SYNOPSIS_LINE_HEIGHT,
                    color = AnixThemeTokens.colors.textSecondary72,
                ),
        )
    }
}

/** Общий стиль заголовков внутри hero-карточки — 14px/700 Manrope, макет применяет его
 *  одинаково к Episodes/Synopsis/Comments (см. те же вызовы в [ReleaseEpisodesSection]/
 *  `ReleaseDetailsScreen.CommentsLinkRow`). */
@Composable
private fun HeroSectionTitle(text: String) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.titleSmall.copy(
                fontSize = HERO_SECTION_TITLE_SIZE,
                fontWeight = FontWeight.Bold,
            ),
    )
}

private fun Release.episodesLabel(): String? {
    val total = episodesTotal
    val released = episodesReleased
    return when {
        total == null && released == null -> null
        total == null -> released.toString()
        else -> "$released/$total"
    }
}

private fun ReleaseStatus.toDisplayName(strings: Strings): String? =
    when (this) {
        ReleaseStatus.ANNOUNCE -> strings.releaseStatusAnnounce
        ReleaseStatus.ONGOING -> strings.releaseStatusOngoing
        ReleaseStatus.FINISHED -> strings.releaseStatusFinished
        ReleaseStatus.UNKNOWN -> null
    }

internal fun LoadError?.toReleaseMessage(strings: Strings): String =
    when (this) {
        LoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        LoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        LoadError.GENERIC, null -> strings.releaseLoadError
    }

/**
 * P13.T7 [FIX]: [detailsError] — провал только под-запроса расширенных метаданных (студия/страна/
 * режиссёр/сезон и т.п., шапки `details`), не всего релиза. Раньше здесь переиспользовался
 * [toReleaseMessage] — на `LoadError.GENERIC` это давало пугающий текст «Не удалось загрузить
 * релиз» с кнопкой «Повторить» посреди уже полностью отрисованной страницы (постер/жанры/эпизоды/
 * рейтинг — всё из [release], не из [details]) — баг, найденный на Desktop при аудите Фазы 13.
 * `NO_CONNECTION`/`UNAUTHORIZED` — общие для всего экрана, текст не меняется.
 */
private fun LoadError?.toDetailsMessage(strings: Strings): String =
    when (this) {
        LoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        LoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        LoadError.GENERIC, null -> strings.releaseDetailsLoadError
    }

private fun formatGrade(grade: Double): String {
    val rounded = (grade * GRADE_ROUNDING_FACTOR).toInt() / GRADE_ROUNDING_FACTOR
    return rounded.toString()
}

private const val GRADE_ROUNDING_FACTOR = 100.0
private val SCREENSHOT_WIDTH = 200.dp
private const val SCREENSHOT_ASPECT_RATIO = 16f / 9f
private val PLAY_SPINNER_STROKE = 2.dp

/** [InfoRow] — размер квадратной подложки под эмодзи-«иконку» пункта метаданных. */
private val INFO_ROW_EMOJI_BOX_SIZE = 36.dp

// ---- [ReleaseNoteBanner]/[parseHexColorOrNull] — см. их KDoc ----
private const val NOTE_BANNER_DARK_LUMINANCE_THRESHOLD = 0.5f
private const val HEX_COLOR_LENGTH_RGB = 6
private const val HEX_COLOR_LENGTH_ARGB = 8
private const val HEX_RADIX = 16

// ---- Wide header genre chips: тот же визуальный язык, что у выбранного чипа
// `AnixFilterChipRow` (primary-акцент, cornerPill, 12sp/600) — read-only, неинтерактивная копия.
private const val GENRE_CHIP_CONTAINER_ALPHA = 0.22f
private const val GENRE_CHIP_BORDER_ALPHA = 0.55f
private val GENRE_CHIP_BORDER_WIDTH = 1.dp
private val GENRE_CHIP_FONT_SIZE = 12.sp

// ---- Hero (phone Compact) — литеральные px-значения макета, не сведены к общим токенам
// AnixDimens/AnixThemeTokens намеренно: это точные пиксельные величины ОДНОГО конкретного места
// макета (`showDetail`), а не переиспользуемые design-токены — тот же приём, что уже применяет
// этот файл для SCREENSHOT_WIDTH/PLAY_SPINNER_STROKE выше.
private val HERO_COVER_HEIGHT = 260.dp
private val HERO_CARD_OVERLAP = 30.dp
private val HERO_CARD_HORIZONTAL_PADDING = 18.dp
private const val HERO_GRADIENT_TOP_ALPHA = 0.15f
private const val HERO_GRADIENT_BOTTOM_ALPHA = 0.96f
private val HERO_BACK_BUTTON_SIZE = 36.dp
private const val HERO_BACK_BUTTON_SCRIM_ALPHA = 0.5f
private val HERO_TITLE_SIZE = 22.sp
private const val HERO_META_SEPARATOR = " · "
private const val HERO_GENRE_SEPARATOR = " • "
private val HERO_RATING_STAR_SIZE = 14.dp
private val HERO_RATING_TEXT_SIZE = 13.sp
private val HERO_GENRE_TEXT_SIZE = 11.sp
private val HERO_BUTTON_HEIGHT = 46.dp
private val HERO_BUTTON_TEXT_SIZE = 14.sp
private val HERO_PLAY_ICON_SIZE = 18.dp
private val HERO_ADD_BUTTON_TEXT_SIZE = 13.sp
private val HERO_ADD_BUTTON_BORDER_WIDTH = 1.dp
private val HERO_SECTION_TITLE_SIZE = 14.sp
private val HERO_SYNOPSIS_TEXT_SIZE = 13.sp
private val HERO_SYNOPSIS_LINE_HEIGHT = 20.15.sp // 13sp × 1.55 line-height макета

// ---- Desktop Expanded drawer (мокап `showDetail`, 2026-09-15) — литеральные px-значения того
// же блока макета; тот же принцип локальных констант, что и у hero-констант phone Compact выше.
private val DRAWER_COVER_HEIGHT = 230.dp
private val DRAWER_CONTENT_OVERLAP = 20.dp
private val DRAWER_CONTENT_PADDING = 24.dp
private val DRAWER_BACK_BUTTON_SIZE = 34.dp
private val DRAWER_BUTTON_HEIGHT = 44.dp
private val DRAWER_BUTTON_RADIUS = 11.dp
