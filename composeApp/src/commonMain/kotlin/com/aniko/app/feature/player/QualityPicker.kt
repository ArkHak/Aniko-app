package com.aniko.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Какие качества предлагать для embed-URL (P16-фикс 2026-09-09).
 *
 * Переключение качества — клиентское, у хоста: Kodik/flowplayer держит собственный
 * `quality-dropdown` (подтверждено live: страница kodikplayer.com идентична для /720p|480p|1080p,
 * качество выбирается в плеере, сегмент URL декоративен — см. P13.T9-CUT и KDoc
 * [EmbedVideoBridge] `switchQuality`). Sibnet/VideoJS без плагина уровней уровней не даёт —
 * список пуст, и чип «Качество» честно не рисуется.
 */
internal fun playerEmbedQualities(embedUrl: String): List<String> =
    if (embedUrl.contains("kodikplayer.com", ignoreCase = true)) {
        listOf("720p", "480p")
    } else {
        emptyList()
    }

/** Текущее качество по умолчанию: сегмент `.../720p` из embed-URL, иначе «720p». */
internal fun currentEmbedQuality(embedUrl: String): String {
    val match = Regex("/([0-9]{3,4}p)(?=/?\\$|\\?|/)").find(embedUrl)
    return match?.groupValues?.get(1) ?: "720p"
}

/** Строка-опция пикера качества: лейбл + маркер текущей. */
@Composable
private fun QualityOptionRow(
    option: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(option) }
                .padding(vertical = dimens.spaceM, horizontal = dimens.spaceS),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            AnixIcon(
                name = "check_circle",
                contentDescription = null,
                filled = true,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Пикер качества поверх кадра плеера (P16-фикс 2026-09-09) — как [AudioPickerOverlay]: тот же
 * скрим + панель снизу. Выбор шлёт [EmbedVideoController.setQuality] хосту (Kodik quality-dropdown);
 * оптимистично обновляет лейбл чипа.
 */
@Composable
internal fun QualityPickerOverlay(
    options: List<String>,
    current: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.posterScrim)
                .clickableNoIndication(onDismiss),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceM),
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = dimens.cornerL, topEnd = dimens.cornerL),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(dimens.spaceM)) {
                    Text(
                        text = strings.playerQualityTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    options.forEach { option ->
                        QualityOptionRow(
                            option = option,
                            selected = option == current,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}
