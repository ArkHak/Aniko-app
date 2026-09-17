package com.aniko.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.ui.i18n.LocalStrings

/**
 * Компактный переключатель темы (Light/Dark) — по образу и подобию [AnixLanguagePicker] (тот же
 * `ChipRow`). Жил на `SettingsScreen`, P13.T2 временно перенесла его на `ProfileScreen` под мокап
 * Claude Design; живой фидбек пользователя (2026-09-11) развернул перенос — переключатель снова
 * на `SettingsScreen`, рядом с языком (см. KDoc обоих экранов).
 *
 * [currentMode] — `"light"`/`"dark"` из `ThemeStore`, `null` — «явный выбор не сделан» (первый
 * запуск). Дефолт приложения — светлая тема (макет Home в Claude Design светлый, сверка
 * 2026-09-08), поэтому при `null` подсвеченным показывается Light — чип «Системная» убран, т.к.
 * отдельного режима «следовать системе» больше нет.
 *
 * 2026-09-10: третий чип AMOLED убран — пользователь предпочёл AMOLED-эстетику (чёрные
 * поверхности) дефолтом единственной тёмной темы вместо отдельного варианта (см. `AnixDarkColors`
 * в `shared/ui/.../theme/Color.kt`), а не третьей опцией выбора.
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
        label = { if (it == "light") strings.themeLight else strings.themeDark },
        onClick = onSelect,
        modifier = modifier,
    )
}
