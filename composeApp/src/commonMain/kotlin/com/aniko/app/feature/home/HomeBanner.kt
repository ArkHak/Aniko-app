package com.aniko.app.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aniko.model.InterestingBanner
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
                AnixLoadingState(modifier = bannerPlaceholderModifier(dimens.bannerHeight, dimens.spaceM))

            errorMessage != null && state.items.isEmpty() ->
                AnixErrorState(
                    message = errorMessage,
                    onRetry = onRetry,
                    modifier = bannerPlaceholderModifier(dimens.bannerHeight, dimens.spaceM),
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
                        modifier = Modifier.fillMaxWidth().height(dimens.bannerHeight),
                    ) { page ->
                        val banner = banners[page]
                        BannerSlide(
                            banner = banner,
                            onClick = { banner.releaseId?.let(onBannerClick) },
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

@Composable
private fun BannerSlide(
    banner: InterestingBanner,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val shape = RoundedCornerShape(dimens.corner16)

    Box(modifier = modifier.clip(shape).clickable(onClick = onClick)) {
        AsyncImage(
            model = banner.imageUrl,
            contentDescription = banner.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // Скрим — простой вертикальный градиент (не blur/платформенный эффект, чистый Compose
        // Brush), гарантирует читаемость белого текста поверх произвольного изображения баннера
        // независимо от темы приложения.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, BANNER_SCRIM_COLOR))),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(dimens.spaceM)) {
            Text(
                text = banner.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            banner.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = BANNER_SUBTITLE_ALPHA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

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

private val BANNER_SCRIM_COLOR = Color.Black.copy(alpha = 0.75f)
private const val BANNER_SUBTITLE_ALPHA = 0.85f
private val INDICATOR_DOT_SIZE = 6.dp
private val INDICATOR_DOT_SIZE_SELECTED = 8.dp
private const val INDICATOR_DOT_ALPHA = 0.4f
