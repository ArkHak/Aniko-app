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

/** Экран настроек. Пока единственный пункт — выход из аккаунта. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
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
