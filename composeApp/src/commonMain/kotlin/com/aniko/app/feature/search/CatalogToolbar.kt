package com.aniko.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aniko.model.AnixGenres
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixSearchField
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Единая верхняя панель Каталога на ВСЕХ размерах окна: поле поиска + вкладки «Все/Новинки» +
 * чипы-фильтры «Статус ▾» / «Жанры ▾» + «Сбросить». Заменила боковую колонку фильтров 200.dp
 * (Expanded) и два ряда чипов над выдачей (Compact/Medium): выдача теперь занимает всю ширину.
 *
 * - Medium/Expanded — ОДИН ряд: поле поиска слева (фиксированная максимальная ширина), справа —
 *   ряд чипов; если места мало, горизонтально скроллится только ряд чипов, поле остаётся на месте
 *   и вертикаль не занимается. Меню чипов — выпадающее ([FilterMenuPresentation.Popup]).
 * - Compact — поле поиска на всю ширину, ниже один компактный ряд чипов (скроллится от края до
 *   края экрана). Меню чипов — `ModalBottomSheet` ([FilterMenuPresentation.BottomSheet]) с тем же
 *   содержимым.
 *
 * Фильтр применяется сразу при выборе (интенты `StatusToggled`/`GenreToggled`), кнопки
 * «Применить» нет. Чип «Статус» — одиночный выбор (меню закрывается после выбора), «Жанры» —
 * множественный (меню остаётся открытым, скроллится, закрывается кликом вне/Esc). Активный чип
 * подсвечен акцентом `primary`, показывает выбор («Онгоинг» / «Жанры · 2») и несёт ✕ быстрого
 * сброса именно этого фильтра.
 *
 * Геометрия: каждый интерактивный элемент имеет зону нажатия 48.dp (`AnixDimens.minTouchTarget`),
 * а рисуется «пилюлей» высотой [CHIP_HEIGHT] по центру этой зоны — визуально компактно, но
 * попадать пальцем удобно. Расчёт без `BoxWithConstraints`/`SubcomposeLayout`: раскладка задана
 * обычными `Row`/`Column`, ширина поля — `widthIn`, поэтому нулевых constraints не бывает.
 *
 * Оговорка про поиск по строке: пока [SearchState.query] непуст, сервер игнорирует статус/жанры
 * (см. KDoc `SearchViewModel`); чипы при этом остаются доступными и применятся, когда поле
 * поиска очищено.
 *
 * Координирующий блок двух раскладок (Compact Column / Medium+Expanded Row) — цельный,
 * поэтому `@Suppress("LongMethod")` ниже.
 */
@Suppress("LongMethod")
@Composable
internal fun CatalogToolbar(
    state: SearchState,
    windowSize: AnixWindowSize,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val presentation =
        if (windowSize == AnixWindowSize.Compact) FilterMenuPresentation.BottomSheet else FilterMenuPresentation.Popup
    val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
        AnixSearchField(
            value = state.query,
            onValueChange = { query -> onIntent(SearchIntent.QueryChanged(query)) },
            placeholder = strings.searchPlaceholder,
            clearContentDescription = strings.searchClearContentDescription,
            modifier = fieldModifier,
        )
    }

    if (windowSize == AnixWindowSize.Compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            searchField(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceM)
                    .testTag(CatalogTestTags.SEARCH_FIELD),
            )
            CatalogFilterRow(
                state = state,
                presentation = presentation,
                onIntent = onIntent,
                modifier = Modifier.fillMaxWidth(),
                // Отступ внутри скролла: чипы доезжают до края экрана, а не обрезаются на 16.dp.
                contentPadding = PaddingValues(horizontal = dimens.spaceM),
            )
        }
    } else {
        val searchMaxWidth =
            if (windowSize == AnixWindowSize.Expanded) {
                SEARCH_MAX_WIDTH_EXPANDED
            } else {
                SEARCH_MAX_WIDTH_MEDIUM
            }
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
        ) {
            searchField(
                Modifier
                    .widthIn(min = SEARCH_MIN_WIDTH, max = searchMaxWidth)
                    .testTag(CatalogTestTags.SEARCH_FIELD),
            )
            CatalogFilterRow(
                state = state,
                presentation = presentation,
                onIntent = onIntent,
                // fill = false: ряд занимает ровно столько, сколько нужно чипам, но не больше остатка.
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(),
            )
        }
    }
}

/** Как именно показывается меню чипа: у чипа ([Popup]) или шторкой снизу ([BottomSheet]). */
internal enum class FilterMenuPresentation { Popup, BottomSheet }

/** Локальные `testTag`-идентификаторы панели Каталога для UI-тестов (в общий `AnixTestTags` не выносим). */
internal object CatalogTestTags {
    const val SEARCH_FIELD = "catalog_search_field"
    const val STATUS_CHIP = "catalog_status_chip"
    const val STATUS_CLEAR = "catalog_status_clear"
    const val GENRES_CHIP = "catalog_genres_chip"
    const val GENRES_CLEAR = "catalog_genres_clear"
    const val RESET = "catalog_reset"
    const val MENU_STATUS = "catalog_menu_status"
    const val MENU_GENRES = "catalog_menu_genres"
}

/** Ряд «Все|Новинки» + чипы + «Сбросить». Скроллится по горизонтали, если не помещается. */
@Composable
private fun CatalogFilterRow(
    state: SearchState,
    presentation: FilterMenuPresentation,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        CatalogTabSegments(
            selected = state.tab,
            onSelect = { tab -> onIntent(SearchIntent.TabSelected(tab)) },
        )
        StatusFilterChip(
            statusId = state.filter.statusId,
            presentation = presentation,
            onToggle = { id -> onIntent(SearchIntent.StatusToggled(id)) },
        )
        GenresFilterChip(
            genres = state.filter.genres,
            presentation = presentation,
            onToggle = { genre -> onIntent(SearchIntent.GenreToggled(genre)) },
            onClear = { onIntent(SearchIntent.GenresCleared) },
        )
        if (state.hasActiveFilters) {
            ResetFiltersButton(onClick = { onIntent(SearchIntent.FiltersReset) })
        }
    }
}

// ---- Вкладки «Все/Новинки» -------------------------------------------------------------------

/**
 * Сегментированный переключатель «Все | Новинки»: пилюля-контейнер, выбранный сегмент — плашка с
 * тинтом `primary`. Единственный выбор, семантика — `Role.Tab` + `selected` в `selectableGroup`.
 */
@Composable
private fun CatalogTabSegments(
    selected: CatalogTab,
    onSelect: (CatalogTab) -> Unit,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    val primary = MaterialTheme.colorScheme.primary
    val pill = RoundedCornerShape(dimens.cornerPill)
    val entries = CatalogTab.entries
    ChipSurface(active = false, open = false, modifier = Modifier.selectableGroup()) {
        entries.forEachIndexed { index, tab ->
            val isSelected = tab == selected
            val label = tab.label(strings)
            val zoneShape =
                when (index) {
                    0 -> RoundedCornerShape(topStart = dimens.cornerPill, bottomStart = dimens.cornerPill)
                    entries.lastIndex -> RoundedCornerShape(topEnd = dimens.cornerPill, bottomEnd = dimens.cornerPill)
                    else -> RoundedCornerShape(0.dp)
                }
            PillZone(
                onTap = { onSelect(tab) },
                overlayShape = zoneShape,
                semantics = {
                    contentDescription = label
                    role = Role.Tab
                    this.selected = isSelected
                },
            ) {
                // Плашка выбранного сегмента: на SEGMENT_INSET меньше «пилюли» с каждой стороны.
                val thumb =
                    if (isSelected) {
                        Modifier
                            .background(primary.copy(alpha = TINT_ALPHA), pill)
                            .border(CHIP_BORDER_WIDTH, primary.copy(alpha = ACTIVE_BORDER_ALPHA), pill)
                    } else {
                        Modifier
                    }
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = SEGMENT_INSET)
                            .height(CHIP_HEIGHT - SEGMENT_INSET * 2)
                            .then(thumb)
                            .padding(horizontal = SEGMENT_HORIZONTAL_PADDING),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (isSelected) 1f else UNSELECTED_LABEL_ALPHA,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun CatalogTab.label(strings: Strings): String =
    when (this) {
        CatalogTab.All -> strings.catalogTabAll
        CatalogTab.New -> strings.catalogTabNew
    }

// ---- Чипы «Статус» и «Жанры» ------------------------------------------------------------------

/**
 * Чип «Статус ▾» — одиночный выбор из [STATUS_OPTIONS]. Меню закрывается после выбора; повторный
 * выбор уже активного статуса снимает его (то же поведение, что у `StatusToggled` во ViewModel).
 */
@Composable
private fun StatusFilterChip(
    statusId: Int?,
    presentation: FilterMenuPresentation,
    onToggle: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    val selectedLabel = STATUS_OPTIONS.firstOrNull { it.id == statusId }?.label?.invoke(strings)
    val active = statusId != null
    // Неизвестный id (например, из deep link) всё равно фильтрует выдачу — показываем чип активным
    // с обычной подписью «Статус», чтобы ✕ был доступен, а выбор не оставался невидимым.
    val text = selectedLabel ?: strings.catalogStatusChipLabel
    FilterMenuChip(
        text = text,
        description =
            if (active) {
                strings.catalogFilterChipActiveDescription(strings.catalogStatusChipLabel, text)
            } else {
                strings.catalogStatusChipLabel
            },
        active = active,
        clearContentDescription = strings.catalogStatusClearContentDescription,
        onClear = { statusId?.let(onToggle) },
        chipTag = CatalogTestTags.STATUS_CHIP,
        clearTag = CatalogTestTags.STATUS_CLEAR,
        presentation = presentation,
        menuTitle = strings.catalogStatusChipLabel,
        menuTag = CatalogTestTags.MENU_STATUS,
    ) { close ->
        OptionChipFlow {
            STATUS_OPTIONS.forEach { option ->
                OptionChip(
                    label = option.label(strings),
                    selected = option.id == statusId,
                    onTap = {
                        onToggle(option.id)
                        close()
                    },
                )
            }
        }
    }
}

/**
 * Чип «Жанры ▾ · N» — множественный выбор из `AnixGenres.popular`. Меню остаётся открытым при
 * выборе; значения жанров — строки API, их не переводим.
 */
@Composable
private fun GenresFilterChip(
    genres: Set<String>,
    presentation: FilterMenuPresentation,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    val strings = LocalStrings.current
    val count = genres.size
    val active = count > 0
    FilterMenuChip(
        text = if (active) strings.catalogGenresChipSelected(count) else strings.catalogGenresChipLabel,
        description =
            if (active) {
                strings.catalogGenresChipSelectedDescription(count)
            } else {
                strings.catalogGenresChipLabel
            },
        active = active,
        clearContentDescription = strings.catalogGenresClearContentDescription,
        onClear = onClear,
        chipTag = CatalogTestTags.GENRES_CHIP,
        clearTag = CatalogTestTags.GENRES_CLEAR,
        presentation = presentation,
        menuTitle = strings.catalogGenresChipLabel,
        menuTag = CatalogTestTags.MENU_GENRES,
    ) { _ ->
        OptionChipFlow {
            AnixGenres.popular.forEach { genre ->
                OptionChip(label = genre, selected = genre in genres, onTap = { onToggle(genre) })
            }
        }
    }
}

/**
 * Общий каркас чипа с меню: пилюля [ChipSurface] из «тела» (подпись + шеврон; тап открывает/
 * закрывает меню) и, когда [active], отдельной зоны ✕ (быстрый сброс). Меню рисуется в [Box]-якоре
 * рядом с чипом — [FilterMenuHost] выбирает выпадающее меню или шторку по [presentation].
 *
 * Семантика тела — `Role.DropdownList` + `selected` (активен ли фильтр) + действия `expand`/
 * `collapse` (скринридер озвучивает свёрнутое/развёрнутое состояние); ✕ — отдельный узел-кнопка,
 * поэтому он НЕ вложен в тело (`clearAndSetSemantics` тела стёр бы его семантику).
 *
 * Данные чипа + два тега + тексты меню; каркас цельный (тело + ✕ + хост) — делить ради лимита
 * вредно, поэтому `@Suppress("LongParameterList", "LongMethod")` ниже.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun FilterMenuChip(
    text: String,
    description: String,
    active: Boolean,
    clearContentDescription: String,
    onClear: () -> Unit,
    chipTag: String,
    clearTag: String,
    presentation: FilterMenuPresentation,
    menuTitle: String,
    menuTag: String,
    menuContent: @Composable (close: () -> Unit) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    var open by remember { mutableStateOf(false) }
    // Значение, а не делегат: лямбда семантики захватывает Boolean и пересоздаётся при смене
    // состояния — иначе действие expand/collapse могло бы остаться от прошлого состояния.
    val isOpen = open
    val contentColor = MaterialTheme.colorScheme.onSurface
    Box {
        ChipSurface(active = active, open = isOpen) {
            PillZone(
                onTap = { open = !open },
                overlayShape =
                    if (active) {
                        RoundedCornerShape(topStart = dimens.cornerPill, bottomStart = dimens.cornerPill)
                    } else {
                        RoundedCornerShape(dimens.cornerPill)
                    },
                testTag = chipTag,
                semantics = {
                    contentDescription = description
                    role = Role.DropdownList
                    this.selected = active
                    if (isOpen) {
                        collapse {
                            open = false
                            true
                        }
                    } else {
                        expand {
                            open = true
                            true
                        }
                    }
                },
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            start = CHIP_HORIZONTAL_PADDING,
                            end = if (active) 0.dp else CHIP_TRAILING_PADDING,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!active) {
                        Spacer(Modifier.size(CHIP_CONTENT_GAP))
                        ChevronDown(tint = contentColor.copy(alpha = ICON_ALPHA), open = isOpen)
                    }
                }
            }
            if (active) {
                PillZone(
                    onTap = onClear,
                    overlayShape = RoundedCornerShape(topEnd = dimens.cornerPill, bottomEnd = dimens.cornerPill),
                    testTag = clearTag,
                    semantics = {
                        contentDescription = clearContentDescription
                        role = Role.Button
                    },
                ) {
                    AnixIcon(
                        name = "close",
                        contentDescription = null,
                        modifier = Modifier.size(CLEAR_ICON_SIZE),
                        tint = contentColor.copy(alpha = ICON_ALPHA),
                    )
                }
            }
        }
        FilterMenuHost(
            open = open,
            onClose = { open = false },
            presentation = presentation,
            title = menuTitle,
            menuTag = menuTag,
            onResetSection = if (active) onClear else null,
            content = menuContent,
        )
    }
}

/** Кнопка «Сбросить»: текст акцентным цветом без рамки; сбрасывает все фильтры сразу. */
@Composable
private fun ResetFiltersButton(onClick: () -> Unit) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    PillZone(
        onTap = onClick,
        overlayShape = RoundedCornerShape(dimens.cornerPill),
        testTag = CatalogTestTags.RESET,
        semantics = {
            contentDescription = strings.catalogFiltersResetContentDescription
            role = Role.Button
        },
    ) {
        Text(
            text = strings.filterChipReset,
            style = MaterialTheme.typography.labelLarge,
            // primaryText — токен «акцент как цвет ТЕКСТА»: проходит AA 4.5:1 на фоне страницы в обеих темах.
            color = AnixThemeTokens.colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = CHIP_HORIZONTAL_PADDING),
        )
    }
}

// ---- Статусы ------------------------------------------------------------------------------------

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

// ---- Метрики (размеры зон нажатия — из `AnixDimens.minTouchTarget`) ------------------------------

private val CHIP_TRAILING_PADDING = 12.dp
private val CLEAR_ICON_SIZE = 16.dp
private val SEGMENT_INSET = 3.dp
private val SEGMENT_HORIZONTAL_PADDING = 12.dp
private val SEARCH_MIN_WIDTH = 200.dp
private val SEARCH_MAX_WIDTH_EXPANDED = 320.dp
private val SEARCH_MAX_WIDTH_MEDIUM = 260.dp

private const val UNSELECTED_LABEL_ALPHA = 0.72f
