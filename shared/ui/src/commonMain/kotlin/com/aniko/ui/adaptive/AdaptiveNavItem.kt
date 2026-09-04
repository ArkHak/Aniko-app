package com.aniko.ui.adaptive

import androidx.compose.runtime.Immutable

/**
 * Один пункт адаптивной навигации (P5.T5). Чистая модель данных — не знает про
 * `AnixDestination`/`NavController`, вызывающая сторона (composeApp) сама решает, какие разделы
 * приложения существуют и что означает [id].
 *
 * P13/Track B (соответствие макету Claude Design): [icon] — имя иконки Material Symbols Rounded
 * для [com.aniko.ui.component.AnixIcon], не `ImageVector`. Раньше здесь было 2 поля
 * (`icon`/`selectedIcon`) с разными `ImageVector` для обычного/выбранного состояния — в старом
 * семействе Material Icons outline и filled варианты буквально разные векторы. Material Symbols
 * кодирует то же самое одной иконкой через ось FILL переменного шрифта, поэтому состояние
 * "выбрано" передаётся явным `filled = selected` в месте отрисовки
 * (`NavigationBarSlot.kt`/`NavigationRailSlot.kt`/`SidebarSlot.kt`), второе поле стало не нужно.
 */
@Immutable
data class AdaptiveNavItem(
    val id: String,
    val label: String,
    val icon: String,
)
