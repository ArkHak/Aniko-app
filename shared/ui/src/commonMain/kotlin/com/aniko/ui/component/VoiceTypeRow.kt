package com.aniko.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.aniko.model.VoiceType
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.qualityBadgeLabel
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
 *
 * @param accentSelected точное соответствие макету Claude Design (Track A): пикер озвучки плеера
 * (`AudioPickerOverlay`, `showDubPicker`) подсвечивает выбранную строку акцентным тинтом альфа
 * `0.14` + бордер альфа `0.5`, а не дефолтным M3 `primaryContainer`/`onPrimaryContainer` — заметно
 * СВЕТЛЕЕ, чем альфа `0.22` у фильтр-чипов (`ChipRow`/`FilterChip`) в остальном приложении
 * (Title Detail и т.д.). Сознательно НЕ унифицировано с ними — это два разных визуальных паттерна
 * "выбрано" по макету. По умолчанию `false` — единственный существовавший вызывающий
 * (`ReleaseEpisodesSection`) не меняет вид.
 *
 * Аватар/монограмма и бейдж качества берутся из [VoiceType.icon]/[VoiceType.quality] самой
 * модели (P16.T4/T5) — отдельных параметров не требуется.
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod") // 6 параметров зафиксированы контрактом
// компонента (см. KDoc выше); сложность — линейные `when` по (selected, accentSelected) для
// container/contentColor, разбиение добавило бы косвенность ради счётчиков линта.
@Composable
fun VoiceTypeRow(
    voiceType: VoiceType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sameCastAsName: String? = null,
    accentSelected: Boolean = false,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current
    val accent = MaterialTheme.colorScheme.primary
    val containerColor =
        when {
            selected && accentSelected -> accent.copy(alpha = ACCENT_SELECTED_CONTAINER_ALPHA)
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        when {
            selected && accentSelected -> MaterialTheme.colorScheme.onSurface
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val subtitle = voiceTypeSubtitle(voiceType, sameCastAsName, strings)
    val qualityLabel = strings.qualityBadgeLabel(voiceType.quality)
    val rowDescription =
        buildString {
            append(voiceType.name)
            if (qualityLabel != null) append(", ").append(qualityLabel)
            if (subtitle != null) append(", ").append(subtitle)
            voiceType.episodesCount?.let { append(", ").append(strings.releaseEpisodesCount(it)) }
            voiceType.viewCount?.let { append(", ").append(strings.releaseVoiceTypeViewsContentDescription(it)) }
        }
    val shape = RoundedCornerShape(dimens.cornerM)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .background(containerColor)
                .let {
                    if (selected && accentSelected) {
                        val borderColor = accent.copy(alpha = ACCENT_SELECTED_BORDER_ALPHA)
                        it.border(BorderStroke(ACCENT_SELECTED_BORDER_WIDTH, borderColor), shape)
                    } else {
                        it
                    }
                }.padding(horizontal = dimens.spaceM, vertical = dimens.space12)
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

/** Левая часть строки — аватар/монограмма, имя, бейдж качества, [SubBadge] и подпись. */
@Composable
private fun RowScope.VoiceTypeInfo(
    voiceType: VoiceType,
    sameCastAsName: String?,
    contentColor: Color,
    strings: Strings,
) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceTypeAvatar(name = voiceType.name, iconUrl = voiceType.icon, size = VOICE_TYPE_AVATAR_SIZE)
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
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
                QualityBadge(quality = voiceType.quality, strings = strings)
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
                AnixIcon(
                    name = "visibility",
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

/**
 * Бейдж качества дорожки (Anixart 10): 1 = "1080p", 2 = "1440p", 3 = "4K".
 * Фон/текст — `primary/onPrimary`: это гарантированная пары контраста ≥ 4.5:1 по спецификации
 * Material Design, что удовлетворяет требованию WCAG 2.1 AA для мелкого текста.
 */
@Composable
private fun QualityBadge(
    quality: Int,
    strings: Strings,
) {
    val label = strings.qualityBadgeLabel(quality)
    if (label != null) {
        Box(
            modifier =
                Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(percent = 50),
                    ).padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Круглый аватар команды озвучки.
 *
 * Если [iconUrl] задан — грузит картинку через Coil (`SubcomposeAsyncImage`) и показывает
 * монограмму-заглушку во время загрузки/при ошибке. Иначе сразу рисует монограмму из инициалов
 * названия команды на подложке `primary`.
 */
@Composable
private fun VoiceTypeAvatar(
    name: String,
    iconUrl: String?,
    size: Dp,
) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { VoiceTypeMonogram(name = name, size = size) },
                error = { VoiceTypeMonogram(name = name, size = size) },
            )
        } else {
            VoiceTypeMonogram(name = name, size = size)
        }
    }
}

/** Круглая заглушка с инициалами названия команды озвучки. */
@Composable
private fun VoiceTypeMonogram(
    name: String,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = voiceTypeInitials(name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Инициалы команды озвучки для монограммы.
 *
 * Берёт первые буквы первых двух "слов" названия (разделитель — пробел/дефис/слеш и т.п.),
 * либо первые две буквы, если слово одно. Всё приводится к верхнему регистру.
 */
private fun voiceTypeInitials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val parts = trimmed.split(Regex("[\\s\\-–—_/|]+")).filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
    } else {
        parts[0].take(2).uppercase()
    }
}

private const val SUBTITLE_ALPHA = 0.7f
private const val COMPACT_THRESHOLD = 1000
private const val DECIMAL_SCALE = 10

/** См. KDoc параметра `accentSelected` у [VoiceTypeRow] — точные альфы макета (`showDubPicker`). */
private const val ACCENT_SELECTED_CONTAINER_ALPHA = 0.14f
private const val ACCENT_SELECTED_BORDER_ALPHA = 0.5f
private val ACCENT_SELECTED_BORDER_WIDTH = 1.dp

/** Размер круглого аватара/монограммы команды озвучки. */
private val VOICE_TYPE_AVATAR_SIZE = 36.dp
