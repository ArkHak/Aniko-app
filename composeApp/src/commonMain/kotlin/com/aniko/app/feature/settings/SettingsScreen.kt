package com.aniko.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aniko.ui.i18n.LocalStrings
import org.koin.compose.viewmodel.koinViewModel

/** Экран настроек: переход в свой профиль, галерею дизайн-токенов и выход из аккаунта. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onDesignGalleryClick: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current

    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            ListItem(
                headlineContent = { Text(text = strings.settingsMyProfile) },
                modifier = Modifier.clickable(onClick = onProfileClick),
            )
            ListItem(
                headlineContent = { Text(text = strings.settingsDesignGallery) },
                modifier = Modifier.clickable(onClick = onDesignGalleryClick),
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = strings.settingsSignOut,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable { viewModel.signOut() },
            )
        }
    }
}
