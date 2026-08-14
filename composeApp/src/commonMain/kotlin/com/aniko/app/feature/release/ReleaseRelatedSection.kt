package com.aniko.app.feature.release

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.model.Release
import com.aniko.ui.component.AnixContentState
import com.aniko.ui.component.HorizontalPosterRail
import com.aniko.ui.component.TitleCard
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * "Похожие тайтлы" / "Рекомендуем" (P7.T11, Трек C) — `ReleaseDetails.relatedReleases`/
 * `recommendedReleases` на [HorizontalPosterRail] (Фаза 6), каждый элемент — [TitleCard].
 *
 * Пустые списки (details ещё не загрузились/упали, см. D1) — [HorizontalPosterRail] сам ничего
 * не рисует для пустого [AnixContentState] без `emptyMessage` (см. его KDoc), поэтому здесь
 * специально не передаётся ни `emptyMessage`, ни отдельная обработка пустоты: секция бесшумно
 * схлопывается до нуля высоты вместо блокирующей ошибки — то самое "пустые секции" из D1.
 */
@Composable
fun ReleaseRelatedSection(
    related: List<Release>,
    recommended: List<Release>,
    onOpenTitle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        HorizontalPosterRail(
            title = strings.titleDetailSimilar,
            state = AnixContentState(items = related),
            key = { it.id },
        ) { release ->
            TitleCard(release = release, onClick = { onOpenTitle(release.id) })
        }

        HorizontalPosterRail(
            title = strings.titleDetailRecommended,
            state = AnixContentState(items = recommended),
            key = { it.id },
        ) { release ->
            TitleCard(release = release, onClick = { onOpenTitle(release.id) })
        }
    }
}
