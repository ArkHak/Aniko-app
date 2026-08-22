package com.aniko.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.aniko.model.VoiceType
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Строка типа озвучки в флоу выбора серии (P8.T6, `docs/REELWAVE_PLAN.md`): имя + бейдж
 * [SubBadge] (только если [VoiceType.isSub], живая верификация подтвердила реальное поле
 * `is_sub`), состав озвучки ([VoiceType.workers]) или заметка «тот же состав, что у X»
 * ([sameCastAsName], см. [com.aniko.model.VoiceType] и вызывающую сторону про сравнение
 * непустых `workers`), количество серий и счётчик просмотров ([VoiceType.viewCount] — тоже
 * реальное поле API, не выдумка под мокап).
 *
 * Бейджа **NEW** здесь намеренно нет: в реальном ответе `GET episode/{releaseId}` нет поля
 * «новая озвучка» — единственный способ его получить (сравнение `episodesCount` с
 * закэшированным прошлым значением, см. `docs/REELWAVE_PLAN.md` про P8.T6) требует истории
 * прошлых значений вне объёма этой задачи, поэтому честно вырезан, а не подделан.
 *
 * Заменяет собой прежний плоский [ChipRow] для списка типов озвучки — тот не мог показать
 * состав/счётчики/бейдж одной строкой, а превращать его в чип с многострочным лейблом было бы
 * хуже читаемо, чем отдельный переиспользуемый компонент строки.
 */
@Composable
fun VoiceTypeRow(
    voiceType: VoiceType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sameCastAsName: String? = null,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val containerColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val subtitle = voiceTypeSubtitle(voiceType, sameCastAsName, strings)
    val rowDescription =
        buildString {
            append(voiceType.name)
            if (subtitle != null) append(", ").append(subtitle)
            voiceType.episodesCount?.let { append(", ").append(strings.releaseEpisodesCount(it)) }
            voiceType.viewCount?.let { append(", ").append(strings.releaseVoiceTypeViewsContentDescription(it)) }
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.cornerM))
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .background(containerColor)
                .padding(horizontal = dimens.spaceM, vertical = dimens.space12)
                // Подтверждено на устройстве (Фаза 11, T9): подписи/счётчики строки не сливаются
                // с кликабельным Row сами по себе — TalkBack фокусировал строку без имени.
                .clearAndSetSemantics {
                    contentDescription = rowDescription
                    role = Role.RadioButton
                    this.selected = selected
                },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceTypeInfo(voiceType, sameCastAsName, contentColor, strings)
        VoiceTypeCounts(voiceType, contentColor, strings)
    }
}

/** Левая часть строки — имя, [SubBadge] и подпись (состав/заметка про тот же состав). */
@Composable
private fun RowScope.VoiceTypeInfo(
    voiceType: VoiceType,
    sameCastAsName: String?,
    contentColor: Color,
    strings: Strings,
) {
    val dimens = AnixThemeTokens.dimens
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = voiceType.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            if (voiceType.isSub) SubBadge()
        }
        voiceTypeSubtitle(voiceType, sameCastAsName, strings)?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = SUBTITLE_ALPHA),
            )
        }
    }
}

/** Правая часть строки — количество серий и счётчик просмотров (оба поля опциональны). */
@Composable
private fun VoiceTypeCounts(
    voiceType: VoiceType,
    contentColor: Color,
    strings: Strings,
) {
    val dimens = AnixThemeTokens.dimens
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        voiceType.episodesCount?.let { count ->
            Text(
                text = strings.releaseEpisodesCount(count),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
        voiceType.viewCount?.let { count ->
            Row(
                modifier =
                    Modifier.semantics {
                        contentDescription = strings.releaseVoiceTypeViewsContentDescription(count)
                    },
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(dimens.badgeIconSize),
                )
                Text(
                    text = formatCompactCount(count),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * Подпись под именем типа озвучки: состав ([VoiceType.workers]), если он непустой, иначе заметка
 * «тот же состав, что у X» ([sameCastAsName], см. KDoc [VoiceTypeRow]), иначе ничего.
 */
private fun voiceTypeSubtitle(
    voiceType: VoiceType,
    sameCastAsName: String?,
    strings: Strings,
): String? =
    when {
        !voiceType.workers.isNullOrBlank() -> voiceType.workers
        sameCastAsName != null -> strings.releaseVoiceTypeSameCast(sameCastAsName)
        else -> null
    }

/**
 * Компактный формат счётчика просмотров (`51287` → `"51.2K"`) без JVM-only `String.format`
 * (аналог [formatGrade] в `Badges.kt`, тот же приём: масштабирование целыми числами).
 */
private fun formatCompactCount(count: Int): String =
    when {
        count < COMPACT_THRESHOLD -> count.toString()
        count < COMPACT_THRESHOLD * COMPACT_THRESHOLD -> compactSuffix(count, COMPACT_THRESHOLD, "K")
        else -> compactSuffix(count, COMPACT_THRESHOLD * COMPACT_THRESHOLD, "M")
    }

private fun compactSuffix(
    count: Int,
    unit: Int,
    suffix: String,
): String {
    val tenths = (count * DECIMAL_SCALE) / unit
    val whole = tenths / DECIMAL_SCALE
    val fraction = tenths % DECIMAL_SCALE
    return "$whole.$fraction$suffix"
}

private const val SUBTITLE_ALPHA = 0.7f
private const val COMPACT_THRESHOLD = 1000
private const val DECIMAL_SCALE = 10
