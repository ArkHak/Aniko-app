package com.aniko.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultFilterQuality
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage

/**
 * Обёртка над Coil `SubcomposeAsyncImage` с аниме-заглушкой [AnimeImagePlaceholder].
 *
 * Пока картинка грузится и при ошибке показывает лайн-арт неко-девочки на градиенте из
 * цветов темы (при загрузке — с shimmer-отблеском, при ошибке — статично); после успеха
 * рендерит изображение так же, как обычный `AsyncImage` (crossfade берётся из синглтон-
 * конфигурации [coil3.SingletonImageLoader], см. `App.kt`).
 *
 * Заменяет «голый» `AsyncImage` там, где важно не оставлять пустую/однотонную плашку во
 * время загрузки: постеры ([AnixPoster]), баннеры Home, hero-обложки, скриншоты, бейджи
 * достижений и т.п. Декоративные места (contentDescription = null) не меняют семантику —
 * заглушка не добавляет accessibility-узлов.
 */
@Suppress("LongParameterList") // Сигнатура намеренно повторяет Coil AsyncImage — это drop-in замена.
@Composable
fun AnixAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DefaultFilterQuality,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        loading = { AnimeImagePlaceholder(modifier = Modifier.matchParentSize()) },
        error = { AnimeImagePlaceholder(modifier = Modifier.matchParentSize(), loading = false) },
    )
}
