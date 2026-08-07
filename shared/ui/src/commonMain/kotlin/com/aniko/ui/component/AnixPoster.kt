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
import coil3.compose.AsyncImage
import com.aniko.ui.theme.AnixThemeTokens

/** Постер релиза со скруглением и фиксированным соотношением сторон. */
@Composable
fun AnixPoster(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val shape = RoundedCornerShape(dimens.cornerM)

    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .width(dimens.posterWidth)
                .aspectRatio(dimens.posterAspectRatio)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape),
    )
}
