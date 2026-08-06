package com.anixkmp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage

/**
 * Круглая аватарка пользователя.
 *
 * Загружает [avatarUrl] через тот же механизм, что и [AnixPoster]/`ReleaseCard`
 * (Coil `AsyncImage`-семейство, синглтон [coil3.SingletonImageLoader]) — здесь
 * используется [SubcomposeAsyncImage], чтобы реактивно подменять контент на заглушку
 * не только когда `avatarUrl == null`, но и пока идёт загрузка или при ошибке.
 *
 * Заглушка — круглая плашка цвета [MaterialTheme.colorScheme.primaryContainer] с первой
 * буквой [login] по центру.
 *
 * Чисто презентационный компонент — не знает про ViewModel/Repository, принимает уже
 * готовые примитивы. Размер настраивается через [size], чтобы переиспользовать как
 * в списках (маленький аватар), так и на экране профиля (крупный).
 */
@Composable
fun AnixAvatar(
    avatarUrl: String?,
    login: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            SubcomposeAsyncImage(
                model = avatarUrl,
                contentDescription = login,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { AvatarFallback(login = login, size = size) },
                error = { AvatarFallback(login = login, size = size) },
            )
        } else {
            AvatarFallback(login = login, size = size)
        }
    }
}

/** Заглушка аватарки: цветной круг с первой буквой логина. Используется, пока нет url, идёт загрузка или произошла ошибка. */
@Composable
private fun AvatarFallback(login: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = login.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (size.value * 0.4f).sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}
