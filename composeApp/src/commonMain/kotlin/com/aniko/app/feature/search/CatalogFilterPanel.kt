package com.aniko.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.model.AnixGenres
import com.aniko.model.CatalogFilter
import com.aniko.ui.component.AnixFilterChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Полный набор фильтров каталога (P7.T3/T6): статус-чипы + жанр-чипы + сброс.
 *
 * Один и тот же композабл для трёх мест использования (см. `SearchScreen.kt`):
 * - Compact/Medium — содержимое `ModalBottomSheet`.
 * - Expanded — боковая панель фиксированной ширины (`Dimens.filterSidebarWidth`), панель сама
 *   ширину себе не назначает — вызывающая сторона задаёт её через [modifier].
 *
 * Год/кол-во серий/длительность/возраст (строка "Доп. фильтры" в аудите P0.T3) — вне объёма
 * P7.T3-T6 (не запрошены брифом трека B), сюда не добавлены.
 */
@Composable
fun CatalogFilterPanel(
    filter: CatalogFilter,
    onStatusToggle: (Int) -> Unit,
    onGenreToggle: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = strings.catalogFiltersTitle, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onReset) { Text(strings.catalogFiltersReset) }
        }

        AnixFilterChipRow(
            items = STATUS_OPTIONS.map { it.id },
            selected = setOfNotNull(filter.statusId),
            label = { id -> STATUS_OPTIONS.first { it.id == id }.label(strings) },
            onToggle = onStatusToggle,
            wrap = true,
        )

        AnixFilterChipRow(
            items = AnixGenres.popular,
            selected = filter.genres,
            label = { genre -> genre },
            onToggle = onGenreToggle,
            wrap = true,
        )
    }
}

/**
 * Статусы каталога — `FilterRequest.status_id` (подтверждено вживую, см. P0.T3): `1` — вышел,
 * `2` — выходит, `3` — анонс. Ровно те же id, что и в [com.aniko.model.CatalogFilter.statusId].
 */
private data class StatusOption(
    val id: Int,
    val label: (Strings) -> String,
)

private const val STATUS_ID_FINISHED = 1
private const val STATUS_ID_ONGOING = 2
private const val STATUS_ID_ANNOUNCE = 3

private val STATUS_OPTIONS =
    listOf(
        StatusOption(STATUS_ID_FINISHED) { it.releaseStatusFinished },
        StatusOption(STATUS_ID_ONGOING) { it.releaseStatusOngoing },
        StatusOption(STATUS_ID_ANNOUNCE) { it.releaseStatusAnnounce },
    )
