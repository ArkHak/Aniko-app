package com.aniko.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.ui.i18n.LocalStrings

/**
 * Компактный переключатель темы (Light/Dark/AMOLED) — используется на Profile под шапкой
 * (P13.T2), по образу и подобию [AnixLanguagePicker] (тот же `ChipRow`).
 *
 * [currentMode] — `"light"`/`"dark"`/`"amoled"` из `ThemeStore`, `null` — «явный выбор не
 * сделан» (первый запуск). Дефолт приложения — светлая тема (макет Home в Claude Design
 * светлый, сверка 2026-09-08), поэтому при `null` подсвеченным показывается Light — чип
 * «Системная» убран, т.к. отдельного режима «следовать системе» больше нет.
 *
 * P16.T20 добавил третий чип AMOLED — та же тёмная тема, но с чисто чёрными поверхностями
 * (см. `AnixAmoledColors`), для OLED-экранов.
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
        items = listOf("light", "dark", "amoled"),
        isSelected = { it == mode },
        label = {
            when (it) {
                "light" -> strings.themeLight
                "amoled" -> strings.themeAmoled
                else -> strings.themeDark
            }
        },
        onClick = onSelect,
        modifier = modifier,
    )
}
