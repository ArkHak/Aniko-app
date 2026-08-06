package com.anixkmp.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel

/** Экран настроек: переход в свой профиль и выход из аккаунта. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            ListItem(
                headlineContent = { Text(text = "Мой профиль") },
                modifier = Modifier.clickable(onClick = onProfileClick),
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = "Выйти",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable { viewModel.signOut() },
            )
        }
    }
}
