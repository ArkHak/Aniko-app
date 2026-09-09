package com.aniko.ui.i18n

/**
 * Человекочитаемый бейдж качества дорожки озвучки по коду `quality` (P16.T4, референс —
 * декомпилированный `QualityBadge.java` официального Anixart 10):
 * `1` = "1080p", `2` = "1440p", `3` = "4K", любое другое значение — `null` (бейдж не рисуется).
 *
 * Единственный источник маппинга: используется и в строках списка озвучек
 * ([VoiceTypeRow]), и в чипах источников серий.
 */
fun Strings.qualityBadgeLabel(quality: Int): String? =
    when (quality) {
        1 -> qualityBadge1080p
        2 -> qualityBadge1440p
        QUALITY_4K_CODE -> qualityBadge4k
        else -> null
    }

private const val QUALITY_4K_CODE = 3
