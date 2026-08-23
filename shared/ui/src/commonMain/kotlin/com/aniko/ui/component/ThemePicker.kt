package com.aniko.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.ui.i18n.LocalStrings

/**
 * Компактный переключатель темы — используется в Settings, по образу и подобию
 * [AnixLanguagePicker] (тот же `ChipRow` с `listOf(null, "light", "dark")`).
 *
 * [currentMode] — `null` означает "следовать системной теме", `"light"`/`"dark"` — явный выбор.
 */
@Composable
fun AnixThemePicker(
    currentMode: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    ChipRow(
        items = listOf(null, "light", "dark"),
        isSelected = { it == currentMode },
        label = {
            when (it) {
                "light" -> strings.themeLight
                "dark" -> strings.themeDark
                else -> strings.themeSystem
            }
        },
        onClick = onSelect,
        modifier = modifier,
    )
}
