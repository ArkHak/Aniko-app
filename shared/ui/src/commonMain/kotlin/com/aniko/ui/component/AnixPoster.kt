package com.aniko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Постер релиза со скруглением и фиксированным соотношением сторон.
 *
 * @param width Фиксированная ширина постера. `null` означает, что ширину диктует
 * родительский `Modifier` (нужно для ячеек grid-раскладок вроде `EpisodeGrid`/
 * `HorizontalPosterRail`, Фаза 6) — в этом случае `.width(...)` не применяется вовсе.
 * @param aspectRatio Соотношение сторон постера, по умолчанию — токен
 * [com.aniko.ui.theme.AnixDimens.posterAspectRatio].
 */
@Composable
fun AnixPoster(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    width: Dp? = AnixThemeTokens.dimens.posterWidth,
    aspectRatio: Float = AnixThemeTokens.dimens.posterAspectRatio,
) {
    val dimens = AnixThemeTokens.dimens
    val shape = RoundedCornerShape(dimens.cornerM)
    val posterDescription = contentDescription

    AnixAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .let { base -> if (width != null) base.width(width) else base }
                .aspectRatio(aspectRatio)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                // На устройстве (Фаза 11, T9) подтверждено дампом accessibility-дерева: Coil
                // `AsyncImage` рисует картинку внутренним дочерним узлом, чей `contentDescription`
                // не сливается автоматически с узлом [modifier] (на котором висит
                // `combinedClickable` вызывающей стороны — `TitleCard`/`ReleaseCard`) — TalkBack
                // фокусировал кликабельный узел БЕЗ имени. Обычный `semantics(mergeDescendants =
                // true)` здесь не сработал (проверено на эмуляторе), `clearAndSetSemantics`
                // задаёт имя напрямую на узле, где реально висит клик.
                .let { base ->
                    if (posterDescription != null) {
                        base.clearAndSetSemantics { this.contentDescription = posterDescription }
                    } else {
                        base
                    }
                },
    )
}
