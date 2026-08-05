package com.anixkmp.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anixkmp.ui.component.AnixEmptyBox

/** Заглушка экрана настроек — наполняется в фазе «аккаунт/настройки». */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    AnixEmptyBox(message = "Настройки появятся здесь", modifier = modifier)
}
