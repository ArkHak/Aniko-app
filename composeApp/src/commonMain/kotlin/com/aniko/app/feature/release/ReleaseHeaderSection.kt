// TooManyFunctions: файл — набор мелких приватных presentation-хелперов шапки Title Detail
// (постер+инфо/watch-ряд/жанры/метаданные/скриншоты/info-строка + form-мапперы), само дробление
// на маленькие функции и есть способ держать `ReleaseHeaderSection` короткой (см. её же
// `LongMethod`-рефакторинг) — сливать их обратно ради счётчика было бы шагом назад.
@file:Suppress("TooManyFunctions")

package com.aniko.app.feature.release

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.ReleaseDetails
import com.aniko.model.ReleaseStatus
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixPoster
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.displayName
import com.aniko.ui.theme.AnixDimens
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Шапка Title Detail (P7.T7, Трек C): постер, названия, метаданные, жанры, кнопка "Смотреть",
 * статус в списке/избранное, скриншоты, синопсис.
 *
 * [details] может быть `null` (ещё грузится или упала независимо от базового [release], см. D1 в
 * KDoc [ReleaseDetailsUiState]) — секции, целиком зависящие от неё (расширенные метаданные,
 * скриншоты), в этом случае просто не рисуются, а не блокируют всю шапку.
 */
@Suppress("LongParameterList") // Публичная сигнатура шапки: 8 обязательных колбэков/данных ровно
// по числу независимых интерактивных зон (плей/статус/избранное/поделиться/ретрай), группировка в
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

        val screenshots = details?.screenshotUrls.orEmpty()
        if (screenshots.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                Text(text = strings.titleDetailScreenshots, style = MaterialTheme.typography.titleMedium)
                ScreenshotRail(urls = screenshots)
            }
        }

        val description = release.description
        if (!description.isNullOrBlank()) {
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Постер + название/альт.названия/базовые info-строки — вынесено из [ReleaseHeaderSection]
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
                )
            }
            val titleAlt = details?.titleAlt
            if (!titleAlt.isNullOrBlank() && titleAlt != release.title && titleAlt != originalTitle) {
                Text(
                    text = titleAlt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
                )
            }

            InfoRow(label = strings.releaseInfoYear, value = release.year?.toString())
            InfoRow(label = strings.releaseInfoStatus, value = release.status.toDisplayName(strings))
            InfoRow(label = strings.releaseInfoEpisodesLabel, value = release.episodesLabel())
            InfoRow(label = strings.releaseInfoRating, value = release.grade?.let { formatGrade(it) })
        }
    }
}

/** Кнопка "Смотреть" + тоггл избранного/статуса списка/поделиться в одном ряду верхних действий. */
@Suppress("LongParameterList", "LongMethod")
// LongParameterList: 6 параметров ровно по числу независимых интерактивных зон
// (плей/статус/избранное/поделиться), та же причина, что у `ReleaseHeaderSection` выше.
// LongMethod: за порог (60) вывели `clearAndSetSemantics{}`-модификаторы на Watch/избранное/
// поделиться (Фаза 11, T9 — IconButton/Button не сливают contentDescription сами по себе, см.
// их KDoc) — тело осталось линейным, разбиение добавило бы косвенность ради счётчика строк.
@Composable
private fun WatchAndFavoriteRow(
    release: Release,
    isResolvingPlay: Boolean,
    onWatchClick: () -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        // Подтверждено на устройстве (Фаза 11, T9): M3 Button не сливает свой Text{} в
        // озвучиваемый узел (тот же паттерн, что и остальные M3-компоненты этой фазы).
        Button(
            onClick = onWatchClick,
            enabled = !isResolvingPlay,
            modifier = Modifier.clearAndSetSemantics { contentDescription = strings.titleDetailWatch },
        ) {
            if (isResolvingPlay) {
                CircularProgressIndicator(
                    modifier = Modifier.width(ButtonDefaults.IconSize),
                    strokeWidth = PLAY_SPINNER_STROKE,
                )
            } else {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            }
            Text(
                text = strings.titleDetailWatch,
                modifier = Modifier.padding(start = dimens.spaceXs),
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
                Icon(
                    imageVector = if (release.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
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
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                )
            }
            ChipRow(
                items = ListStatus.entries,
                isSelected = { it == release.myListStatus },
                label = { it.displayName(strings) },
                onClick = { status -> onChangeListStatus(if (status == release.myListStatus) null else status) },
            )
        }
    }
}

/** Жанры отдельными нередактируемыми чипами (не строка через запятую) — `Release.genres` уже
 * распарсен в список маппером (`ReleaseMapper.toDomain`), здесь только отрисовка. */
@Composable
private fun GenreChipRow(genres: List<String>) {
    val dimens = AnixThemeTokens.dimens
    val shape = RoundedCornerShape(dimens.cornerPill)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        genres.forEach { genre ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = shape) {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = dimens.spaceS, vertical = dimens.spaceXs),
                )
            }
        }
    }
}

/** Расширенные метаданные (P7.T7) — только поля из [ReleaseDetails], каждое опционально. */
@Composable
private fun MetadataSection(details: ReleaseDetails?) {
    if (details == null) return
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        InfoRow(label = strings.titleDetailStudio, value = details.studio)
        InfoRow(label = strings.titleDetailCountry, value = details.country)
        InfoRow(label = strings.titleDetailAuthor, value = details.author)
        InfoRow(label = strings.titleDetailDirector, value = details.director)
        InfoRow(label = strings.titleDetailSeason, value = details.season)
        InfoRow(label = strings.titleDetailReleaseDate, value = details.releaseDate)
        InfoRow(label = strings.titleDetailAgeRating, value = details.ageRating)
        InfoRow(label = strings.titleDetailEpisodeDuration, value = details.duration?.toString())
        InfoRow(label = strings.titleDetailCategory, value = details.category)
        InfoRow(label = strings.titleDetailSource, value = details.source)
        InfoRow(label = strings.titleDetailTranslators, value = details.translators)
    }
}

@Composable
private fun ScreenshotRail(urls: List<String>) {
    val dimens = AnixThemeTokens.dimens
    val shape = RoundedCornerShape(dimens.cornerM)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        items(items = urls, key = { it }) { url ->
            AsyncImage(
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
) {
    if (value.isNullOrBlank()) return
    val dimens = AnixThemeTokens.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
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
 * режиссёр/сезон и т.п., [ReleaseHeaderSection]'s `details`), не всего релиза. Раньше здесь
 * переиспользовался [toReleaseMessage] — на `LoadError.GENERIC` это давало пугающий текст «Не
 * удалось загрузить релиз» с кнопкой «Повторить» посреди уже полностью отрисованной страницы
 * (постер/жанры/эпизоды/рейтинг — всё из [release], не из [details]) — баг, найденный на Desktop
 * при аудите Фазы 13. `NO_CONNECTION`/`UNAUTHORIZED` — общие для всего экрана, текст не меняется.
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

private const val SECONDARY_TEXT_ALPHA = 0.7f
private const val GRADE_ROUNDING_FACTOR = 100.0
private val SCREENSHOT_WIDTH = 200.dp
private const val SCREENSHOT_ASPECT_RATIO = 16f / 9f
private val PLAY_SPINNER_STROKE = 2.dp
