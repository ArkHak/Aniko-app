@file:Suppress("ForbiddenCyrillicStringLiteral", "MagicNumber")

package com.aniko.app.feature.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.ReleaseStatus
import com.aniko.ui.component.AnixContentState
import com.aniko.ui.component.AnixFilterChipRow
import com.aniko.ui.component.HorizontalPosterRail
import com.aniko.ui.component.ListStatusChip
import com.aniko.ui.component.ListStatusChipStyle
import com.aniko.ui.component.ProgressRow
import com.aniko.ui.component.StatTileData
import com.aniko.ui.component.StatTileRow
import com.aniko.ui.component.TitleCard
import com.aniko.ui.component.TitleCardLayout
import com.aniko.ui.component.chart.BarEntry
import com.aniko.ui.component.chart.ChartSlice
import com.aniko.ui.component.chart.DonutChart
import com.aniko.ui.component.chart.RatingHistogram
import com.aniko.ui.component.chart.RatingInput
import com.aniko.ui.component.chart.WeeklyBarChart
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Витрина всех компонентов Фазы 6 на фейковых данных — одновременно демонстрация и инструмент
 * аудита P6.T12: вместе с переключателями языка/масштаба шрифта в `TokenGalleryScreen`
 * (`GalleryControls`) позволяет визуально проверить каждый компонент на RU/EN и на всех трёх
 * масштабах шрифта в одном месте, без отдельной screenshot-инфраструктуры (см. её отсутствие в
 * проекте — журнал Фазы 6).
 *
 * Вынесена в отдельный файл от `TokenGalleryScreen.kt` (detekt `TooManyFunctions`/`LongMethod` —
 * один экран на секции P2.T12 + P6.T12 разросся за лимиты), разбита на по-компонентно короткие
 * `*Demo`-функции по той же причине.
 *
 * `@Suppress("ForbiddenCyrillicStringLiteral")`: как и в `TypographyRow` (`TokenGalleryScreen.kt`)
 * — фейковые демонстрационные данные витрины, не реальный UI-текст приложения. Кириллица нужна
 * для визуальной проверки переполнения RU-строк независимо от текущего языка приложения — через
 * Strings/Lyricist это не сделать (один активный язык), тот же принцип, что уже задокументирован
 * там же.
 *
 * `@Suppress("MagicNumber")`: значения (id/оценки/счётчики/веса баров) — произвольные фейковые
 * демо-данные для визуальной проверки, не содержательные константы продукта — тот же принцип, что
 * уже применён к `AnixPalette` (`shared/ui/.../theme/Color.kt`). Оба suppress — на уровне файла
 * (`@file:Suppress`), так как относятся ко всем демо-функциям ниже, а не только к одной.
 */
@Composable
internal fun ComponentsSection() {
    val dimens = AnixThemeTokens.dimens
    val sampleRelease = sampleRelease()
    val announcedRelease =
        sampleRelease.copy(id = 2, status = ReleaseStatus.ANNOUNCE, myListStatus = null, isFavorite = false)

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceL)) {
        TitleCardDemo(sampleRelease, announcedRelease)
        ChipsDemo()
        ProgressRowDemo(sampleRelease)
        HorizontalPosterRailDemo(sampleRelease, announcedRelease)
        StatTileDemo()
        ChartsDemo()
        RatingDemo()
    }
}

private fun sampleRelease(): Release =
    Release(
        id = 1,
        title = "Очень длинное название тайтла, которое обязано аккуратно обрезаться в двух строках",
        posterUrl = null,
        episodesTotal = 24,
        episodesReleased = 12,
        grade = 8.7,
        status = ReleaseStatus.ONGOING,
        myListStatus = ListStatus.WATCHING,
        isFavorite = true,
    )

@Composable
private fun TitleCardDemo(
    sampleRelease: Release,
    announcedRelease: Release,
) {
    val dimens = AnixThemeTokens.dimens
    GalleryComponentRow("TitleCard (Grid)") {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
            TitleCard(release = sampleRelease, onClick = {})
            TitleCard(release = announcedRelease, onClick = {}, isNewEpisode = true)
        }
    }
    GalleryComponentRow("TitleCard (List)") {
        TitleCard(release = sampleRelease, onClick = {}, layout = TitleCardLayout.List, subtitle = "2024 · 24 эп.")
    }
}

@Composable
private fun ChipsDemo() {
    val dimens = AnixThemeTokens.dimens
    GalleryComponentRow("ListStatusChip") {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
            ListStatus.entries.forEach { ListStatusChip(it, style = ListStatusChipStyle.Full) }
        }
    }
    GalleryComponentRow("AnixFilterChipRow (множественный выбор + перенос строк)") {
        AnixFilterChipRow(
            items = listOf("Сёнэн", "Экшен", "Драма", "Комедия", "Фэнтези", "Романтика"),
            selected = setOf("Экшен", "Драма"),
            label = { it },
            onToggle = {},
            wrap = true,
        )
    }
}

@Composable
private fun ProgressRowDemo(sampleRelease: Release) {
    GalleryComponentRow("ProgressRow") {
        ProgressRow(
            posterUrl = null,
            title = sampleRelease.title,
            watchedEpisodes = 8,
            totalEpisodes = 24,
            subtitle = "Продолжить просмотр",
            onClick = {},
        )
    }
}

@Composable
private fun HorizontalPosterRailDemo(
    sampleRelease: Release,
    announcedRelease: Release,
) {
    GalleryComponentRow("HorizontalPosterRail") {
        HorizontalPosterRail(
            title = "Продолжить смотреть",
            state = AnixContentState(items = listOf(sampleRelease, announcedRelease)),
            key = { it.id },
            item = { TitleCard(release = it, onClick = {}) },
        )
    }
}

@Composable
private fun StatTileDemo() {
    GalleryComponentRow("StatTileRow") {
        StatTileRow(
            tiles =
                listOf(
                    StatTileData("128", "Смотрю"),
                    StatTileData("512", "Просмотрено"),
                    StatTileData("34", "В планах"),
                    StatTileData("6", "Брошено"),
                ),
        )
    }
}

@Composable
private fun ChartsDemo() {
    val dimens = AnixThemeTokens.dimens
    GalleryComponentRow("DonutChart") {
        DonutChart(
            slices =
                listOf(
                    ChartSlice("Смотрю", 128f),
                    ChartSlice("Просмотрено", 512f),
                    ChartSlice("В планах", 34f),
                    ChartSlice("Брошено", 6f),
                ),
            modifier = Modifier.size(dimens.chartHeight),
        )
    }
    GalleryComponentRow("WeeklyBarChart") {
        WeeklyBarChart(
            entries =
                listOf(
                    BarEntry("Пн", 3f),
                    BarEntry("Вт", 1f),
                    BarEntry("Ср", 5f),
                    BarEntry("Чт", 2f),
                    BarEntry("Пт", 4f),
                    BarEntry("Сб", 6f),
                    BarEntry("Вс", 0f),
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RatingDemo() {
    val dimens = AnixThemeTokens.dimens
    GalleryComponentRow("RatingHistogram + RatingInput") {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
            RatingHistogram(counts = listOf(2, 5, 12, 40, 80), averageLabel = "8.7")
            RatingInput(myRating = 4, onRate = {})
        }
    }
}

@Composable
private fun GalleryComponentRow(
    label: String,
    content: @Composable () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        content()
    }
}
