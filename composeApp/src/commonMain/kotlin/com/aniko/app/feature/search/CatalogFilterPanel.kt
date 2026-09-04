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
import androidx.compose.ui.text.style.TextOverflow
import com.aniko.model.AnixGenres
import com.aniko.model.CatalogFilter
import com.aniko.ui.component.AnixFilterChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Полный набор фильтров каталога (P7.T3/T6): статус-чипы + жанр-чипы + сброс.
 *
 * Используется только в `ExpandedCatalogLayout` (`SearchScreen.kt`) — постоянная боковая панель
 * фиксированной ширины (`Dimens.filterSidebarWidth`), панель сама ширину себе не назначает —
 * вызывающая сторона задаёт её через [modifier]. Compact/Medium с P13.T3 переиспользуют не эту
 * панель целиком, а [CatalogInlineFilterChips] ниже — те же данные, но без заголовка/кнопки
 * "Сбросить" и без вертикального скролла, см. её KDoc.
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
            // P13.T7 [FIX]: живая проверка на узкой (~200dp, нижняя граница
            // FILTER_SIDEBAR_MIN_WIDTH в SearchScreen.kt) сайдбар-панели показала перенос по
            // одной букве — `widthIn(min = …)` на самой панели не спасает: она сидит внутри
            // `Row.weight(fill = false)` в `ExpandedCatalogLayout`, а несущий maxWidth от весовой
            // раскладки Row нельзя раздвинуть child-модификатором `widthIn` изнутри (parent-
            // constraint всегда главнее). Единственный надёжный фикс — не полагаться на то, что
            // ширины хватит: `weight(1f)` на заголовке отдаёт кнопке "Сброс" её нужный минимум
            // первой, а `maxLines = 1` + ellipsis на обоих текстах гарантируют одну строку при
            // любой реальной ширине (тот же приём, что и в NavigationBarSlot.kt).
            Text(
                text = strings.catalogFiltersTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = onReset) {
                Text(text = strings.catalogFiltersReset, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
 * Статус-чипы + жанр-чипы как две отдельные горизонтально скроллящиеся строки (P13.T3) — для
 * Compact/Medium `CompactCatalogLayout` (`SearchScreen.kt`), рендерится прямо под полем поиска
 * вместо кнопки "Фильтры" + [CatalogFilterPanel] в `ModalBottomSheet` (см. журнал изменений плана,
 * P13). Мокап Claude Design (`statusChips`/`genreChips`, `overflow-x:auto`) рисует их именно так —
 * рядами без переноса, а не сеткой/сайдбар-колонкой, отсюда `wrap = false` (дефолт
 * [AnixFilterChipRow]) вместо `wrap = true`, который использует [CatalogFilterPanel] для Expanded.
 *
 * Без заголовка "Фильтры" и кнопки "Сбросить" — в мокапе их тоже нет рядом с чипами (сброс —
 * по одному клику на уже активный чип, тот же паттерн, что уже реализован в
 * `SearchViewModel.updateFilter`), и без вертикального скролла — Compact-раскладка сама скроллит
 * весь экран, а не только панель фильтров.
 */
@Composable
fun CatalogInlineFilterChips(
    filter: CatalogFilter,
    onStatusToggle: (Int) -> Unit,
    onGenreToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        AnixFilterChipRow(
            items = STATUS_OPTIONS.map { it.id },
            selected = setOfNotNull(filter.statusId),
            label = { id -> STATUS_OPTIONS.first { it.id == id }.label(strings) },
            onToggle = onStatusToggle,
        )

        AnixFilterChipRow(
            items = AnixGenres.popular,
            selected = filter.genres,
            label = { genre -> genre },
            onToggle = onGenreToggle,
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
