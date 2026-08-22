package com.aniko.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.aniko.model.Release
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Строка прогресса просмотра: компактный постер слева ([AnixDimens.posterWidthS]) + заголовок/
 * подзаголовок/полоска прогресса справа. Три реальных потребителя (Continue Watching/Мои
 * списки/Расписание) считают "сколько посмотрено" по-разному (готовое поле из API у одних,
 * локальный подсчёт [com.aniko.model.Episode.isWatched] у других) — поэтому основная
 * перегрузка принимает примитивы, а не `Release` целиком. `AnixPoster` не даёт параметра
 * ширины (внутри фиксирован `dimens.posterWidth`, см. `AnixPoster.kt`) — здесь это обходится
 * модификатором [Modifier.width]: `Modifier.width()` резолвится в constraint от внешнего к
 * внутреннему (`Constraints.constrain`), так что переданная снаружи `dimens.posterWidthS`
 * зажимает внутренний `.width(dimens.posterWidth)` компонента до этого меньшего значения — без
 * необходимости трогать `AnixPoster.kt` (вне рамок трека B этой фазы).
 */
@Suppress("LongParameterList", "LongMethod")
// LongParameterList: публичная сигнатура зафиксирована брифом P6.T6: примитивы
// posterUrl/title/watchedEpisodes/totalEpisodes/onClick обязательны (три реальных потребителя
// считают "просмотрено" по-разному, см. KDoc класса), subtitle/trailing — опциональные слоты.
// LongMethod: за порог (60) вывел `accessibleLabel`/`clearAndSetSemantics{}` (Фаза 11, T9 —
// Modifier.clickable не сливает потомков сам по себе, см. KDoc ниже) — тело осталось линейным.
@Composable
fun ProgressRow(
    posterUrl: String?,
    title: String,
    watchedEpisodes: Int?,
    totalEpisodes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    val watched = watchedEpisodes ?: 0
    val total = totalEpisodes ?: 0
    val progress = if (total > 0) (watched.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val accessibleLabel =
        buildString {
            append(title)
            if (subtitle != null) append(", ").append(subtitle)
            append(", ").append(strings.progressEpisodesOf(watched, total))
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                // Подтверждено на устройстве (Фаза 11, T9): без явного merge TalkBack фокусирует
                // кликабельную строку без имени, заголовок/прогресс остаются отдельными
                // недостижимыми для навигации узлами (обычный semantics(mergeDescendants=true)
                // тоже не помог, проверено на эмуляторе — см. AnixPoster.kt/AnixNavigationBar.kt).
                .clearAndSetSemantics { contentDescription = accessibleLabel }
                .padding(vertical = dimens.spaceS, horizontal = dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        AnixPoster(
            url = posterUrl,
            contentDescription = title,
            modifier = Modifier.width(dimens.posterWidthS),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(dimens.progressBarHeight)
                        .clip(RoundedCornerShape(dimens.progressBarHeight / 2)),
            )
            Text(
                text = strings.progressEpisodesOf(watched, total),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        trailing?.invoke()
    }
}

/**
 * Перегрузка поверх [Release]: берёт `posterUrl`/`title`/`episodesTotal` из доменной модели.
 * `watchedEpisodes` всё равно передаётся отдельно — [Release] не хранит счётчик просмотренных
 * серий (только `episodesReleased`), а способ его получить разный у каждого потребителя.
 */
@Composable
fun ProgressRow(
    release: Release,
    watchedEpisodes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = ProgressRow(
    posterUrl = release.posterUrl,
    title = release.title,
    watchedEpisodes = watchedEpisodes,
    totalEpisodes = release.episodesTotal,
    onClick = onClick,
    modifier = modifier,
)
