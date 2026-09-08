package com.aniko.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.ui.i18n.LocalStrings

/**
 * Компактный переключатель темы (Light/Dark) — используется на Profile под шапкой (P13.T2),
 * по образу и подобию [AnixLanguagePicker] (тот же `ChipRow`).
 *
 * [currentMode] — `"light"`/`"dark"` из `ThemeStore`, `null` — «явный выбор не сделан» (первый
 * запуск). Дефолт приложения — светлая тема (макет Home в Claude Design светлый, сверка
 * 2026-09-08), поэтому при `null` подсвеченным показывается Light — чип «Системная» убран, т.к.
 * отдельного режима «следовать системе» больше нет.
 */
@Composable
fun AnixThemePicker(
    currentMode: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    // null = дефолт «светлая»: подсвечиваем Light, пока пользователь не выбрал явно.
    val mode = currentMode ?: "light"
    ChipRow(
        items = listOf("light", "dark"),
        isSelected = { it == mode },
        label = {
            when (it) {
                "light" -> strings.themeLight
                else -> strings.themeDark
            }
        },
        onClick = onSelect,
        modifier = modifier,
    )
}
