package com.aniko.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Один пункт адаптивной навигации (P5.T5). Чистая модель данных — не знает про
 * `AnixDestination`/`NavController`, вызывающая сторона (composeApp) сама решает, какие разделы
 * приложения существуют и что означает [id].
 */
@Immutable
data class AdaptiveNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)
