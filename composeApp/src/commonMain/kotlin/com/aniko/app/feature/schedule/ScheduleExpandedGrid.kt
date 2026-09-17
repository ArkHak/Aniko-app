package com.aniko.app.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aniko.model.Release
import com.aniko.model.Schedule
import com.aniko.model.WeekDay
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Desktop-раскладка расписания (`Expanded`): 7 колонок дней на всю ширину контента, без
 * дневного селектора и без дублирующих подписей (мокап Claude Design, desktop-артборд,
 * `isSchedule`, строка 853: `grid-template-columns: repeat(7, 1fr); gap: 14px`, лейбл дня
 * 11sp/700 uppercase с `letter-spacing .05em`, арт 100%×96dp radius 12, имя 11sp/600).
 *
 * Вынесено из `ScheduleScreen.kt` в отдельный файл (detekt `TooManyFunctions` на файле экрана):
 * здесь живёт только Expanded-сетка, Medium по-прежнему рисует горизонтально прокручиваемые
 * колонки фиксированной ширины ([ScheduleColumns] в `ScheduleScreen.kt`), Compact — вертикальный
 * список 7 секций.
 */
@Suppress("LongParameterList") // Координирующий блок: данные расписания + день «сегодня» + колбэк + модификатор.
@Composable
internal fun ScheduleExpandedGrid(
    schedule: Schedule,
    strings: Strings,
    today: WeekDay,
    onReleaseClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.spaceM),
        horizontalArrangement = Arrangement.spacedBy(EXPANDED_DAY_GAP),
    ) {
        WeekDay.entries.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EXPANDED_DAY_INNER_GAP),
            ) {
                Text(
                    text = day.chipLabel(today, strings).uppercase(),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = EXPANDED_DAY_LABEL_FONT_SIZE,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.05.em,
                        ),
                    color = colors.textSecondary60,
                )

                val releases = schedule.releasesOn(day)
                if (releases.isEmpty()) {
                    Text(
                        text = strings.scheduleEmptyDayMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = EXPANDED_EMPTY_FONT_SIZE),
                        color = colors.textSecondary45,
                    )
                } else {
                    releases.forEach { release ->
                        ScheduleExpandedReleaseCard(
                            release = release,
                            onReleaseClick = onReleaseClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка релиза в Expanded-расписании: арт 100%×96dp radius 12, имя 11sp/600, gap 6.
 */
@Composable
private fun ScheduleExpandedReleaseCard(
    release: Release,
    onReleaseClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onReleaseClick(release.id) }
                .semantics(mergeDescendants = true) {
                    contentDescription = release.title
                },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(EXPANDED_CARD_ART_HEIGHT)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = release.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = release.title,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** gap между колонками дней desktop-мокапа (`repeat(7, 1fr); gap: 14px`). */
private val EXPANDED_DAY_GAP = 14.dp

/** gap внутри колонки дня (лейбл → карточки). */
private val EXPANDED_DAY_INNER_GAP = 10.dp

/** Высота арта карточки дня (мокап: 96px). */
private val EXPANDED_CARD_ART_HEIGHT = 96.dp

/** Лейбл дня — 11sp/700 uppercase (мокап). */
private val EXPANDED_DAY_LABEL_FONT_SIZE = 11.sp

/** Надпись пустого дня в Expanded-сетке — 11.5sp (мокап). */
private val EXPANDED_EMPTY_FONT_SIZE = 11.5.sp
