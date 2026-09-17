package com.aniko.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.model.InterestingBanner
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixAsyncImage
import com.aniko.ui.component.AnixContentState
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Баннер-карусель топ-тайтлов (P7.T1) — `HorizontalPager` (не `LazyRow` + snap: пейджер резолвится
 * в этой версии Compose Multiplatform без проблем, см. `gradle/libs.versions.toml` —
 * `composeMultiplatform = 1.11.1`) БЕЗ автопрокрутки: доступность и расход батареи важнее
 * "живости" ленты, пользователь листает сам, точечный индикатор снизу показывает позицию.
 *
 * Клик по слайду ведёт на `banner.releaseId` тем же колбэком, что и остальные секции Home
 * (`onBannerClick(releaseId)`, P7.T1) — слайды без числового `releaseId` (см. KDoc
 * [InterestingBanner]) листаются, но не кликабельны на переход.
 */
@Composable
fun HomeBanner(
    state: AnixContentState<InterestingBanner>,
    onBannerClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val windowSize = LocalAnixWindowSize.current
    // Expanded-гейт высоты: desktop-мокап (строка 757) даёт 220dp, mobile-артборды — 230dp
    // (Фаза 14); менять глобальный токен нельзя — регрессия телефонной раскладки (см. ревью F3).
    val bannerHeight = if (windowSize == AnixWindowSize.Expanded) dimens.bannerHeightExpanded else dimens.bannerHeight

    // Нет баннеров и нет ошибки — секция ничего не занимает (тот же принцип, что и у остальных
    // секций Home: пустая лента не должна "мигать" пустым блоком).
    if (state.isEmpty) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(
            text = strings.homeBannerTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimens.spaceM),
        )

        val errorMessage = state.errorMessage
        when {
            state.isLoading && state.items.isEmpty() ->
                AnixLoadingState(modifier = bannerPlaceholderModifier(bannerHeight, dimens.spaceM))

            errorMessage != null && state.items.isEmpty() ->
                AnixErrorState(
                    message = errorMessage,
                    onRetry = onRetry,
                    modifier = bannerPlaceholderModifier(bannerHeight, dimens.spaceM),
                )

            else -> {
                val banners = state.items
                val pagerState = rememberPagerState(pageCount = { banners.size })

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spaceM),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().height(bannerHeight),
                    ) { page ->
                        val banner = banners[page]
                        BannerSlide(
                            banner = banner,
                            onClick = { banner.releaseId?.let(onBannerClick) },
                            isExpanded = windowSize == AnixWindowSize.Expanded,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    if (banners.size > 1) {
                        BannerPagerIndicator(pageCount = banners.size, currentPage = pagerState.currentPage)
                    }
                }
            }
        }
    }
}

private fun bannerPlaceholderModifier(
    height: Dp,
    horizontalPadding: Dp,
) = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).height(height)

/**
 * [isExpanded] переключает оформление слайда между исходным Compact/Medium видом (вертикальный
 * скрим снизу, заголовок `titleLarge`, паддинг `dimens.spaceM` по периметру) и desktop-мокапом
 * Claude Design (строка 757): горизонтальный скрим слева `rgba(0,0,0,.6) → transparent 65%`,
 * заголовок Manrope 800 30px, подпись 13px, отступ left 32/bottom 24, max-width 420 — правка
 * затрагивает только [AnixWindowSize.Expanded] (бриф desktop-прохода), Compact/Medium не
 * регрессируют.
 */
@Composable
private fun BannerSlide(
    banner: InterestingBanner,
    onClick: () -> Unit,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    // Track C (2026-09-04): макет задаёт радиус баннера 20dp — это `dimens.cornerL`, не
    // `.corner16` (16dp), которым баннер был скруглён раньше.
    val shape = RoundedCornerShape(dimens.cornerL)

    Box(
        modifier =
            modifier
                .clip(shape)
                .clickable(onClick = onClick)
                // Подтверждено на устройстве (Фаза 11, T9): contentDescription на вложенном
                // Coil-изображении (AsyncImage/AnixAsyncImage) не сливается сам по себе с
                // кликабельным Box (обычный
                // semantics(mergeDescendants=true) тоже не сработал, проверено на эмуляторе) —
                // TalkBack фокусировал баннер без имени. clearAndSetSemantics задаёт имя
                // напрямую на кликабельном узле.
                .clearAndSetSemantics { contentDescription = banner.title },
    ) {
        AnixAsyncImage(
            model = banner.imageUrl,
            contentDescription = banner.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(if (isExpanded) expandedScrimBrush() else compactScrimBrush()),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (isExpanded) BANNER_TEXT_PADDING_EXPANDED else PaddingValues(dimens.spaceM))
                    .let { base -> if (isExpanded) base.widthIn(max = BANNER_TEXT_MAX_WIDTH) else base },
            verticalArrangement = Arrangement.spacedBy(if (isExpanded) BANNER_TEXT_GAP else dimens.spaceXs),
        ) {
            Text(
                text = banner.title,
                style = if (isExpanded) expandedTitleStyle() else compactTitleStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            banner.description?.let { description ->
                Text(
                    text = description,
                    style = if (isExpanded) expandedSubtitleStyle() else compactSubtitleStyle(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Desktop (Expanded, мокап строка 757): `rgba(0,0,0,.6) → transparent 65%`, слева направо. */
private fun expandedScrimBrush(): Brush =
    Brush.horizontalGradient(
        BANNER_SCRIM_STOP_START to BANNER_SCRIM_COLOR,
        BANNER_SCRIM_STOP_END to Color.Transparent,
    )

/** Compact/Medium (не тронуто desktop-проходом): прежний вертикальный скрим снизу вверх. */
private fun compactScrimBrush(): Brush =
    Brush.verticalGradient(
        COMPACT_SCRIM_STOP_START to Color.Transparent,
        COMPACT_SCRIM_STOP_END to COMPACT_SCRIM_COLOR,
    )

@Composable
private fun expandedTitleStyle(): TextStyle =
    MaterialTheme.typography.displayMedium.copy(
        fontSize = BANNER_TITLE_FONT_SIZE,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = BANNER_TITLE_LINE_HEIGHT,
        color = Color.White,
    )

@Composable
private fun compactTitleStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(color = Color.White)

@Composable
private fun expandedSubtitleStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(
        fontSize = BANNER_SUBTITLE_FONT_SIZE,
        color = Color.White.copy(alpha = BANNER_SUBTITLE_ALPHA),
    )

@Composable
private fun compactSubtitleStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = COMPACT_SUBTITLE_ALPHA))

@Composable
private fun BannerPagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier =
                    Modifier
                        .size(if (isSelected) INDICATOR_DOT_SIZE_SELECTED else INDICATOR_DOT_SIZE)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = INDICATOR_DOT_ALPHA)
                            },
                        ),
            )
        }
    }
}

// Desktop (Expanded) — мокап Claude Design, строка 757.
private val BANNER_SCRIM_COLOR = Color.Black.copy(alpha = 0.6f)
private const val BANNER_SCRIM_STOP_START = 0f
private const val BANNER_SCRIM_STOP_END = 0.65f
private const val BANNER_SUBTITLE_ALPHA = 0.75f
private val BANNER_TITLE_FONT_SIZE = 30.sp
private val BANNER_TITLE_LINE_HEIGHT = 36.sp
private val BANNER_SUBTITLE_FONT_SIZE = 13.sp
private val BANNER_TEXT_MAX_WIDTH = 420.dp
private val BANNER_TEXT_PADDING_EXPANDED = PaddingValues(start = 32.dp, bottom = 24.dp)
private val BANNER_TEXT_GAP = 8.dp

// Compact/Medium — исходное оформление (Track C, 2026-09-04), не тронуто desktop-проходом.
private val COMPACT_SCRIM_COLOR = Color.Black.copy(alpha = 0.75f)
private const val COMPACT_SCRIM_STOP_START = 0.45f
private const val COMPACT_SCRIM_STOP_END = 1f
private const val COMPACT_SUBTITLE_ALPHA = 0.85f

private val INDICATOR_DOT_SIZE = 6.dp
private val INDICATOR_DOT_SIZE_SELECTED = 8.dp
private const val INDICATOR_DOT_ALPHA = 0.4f
