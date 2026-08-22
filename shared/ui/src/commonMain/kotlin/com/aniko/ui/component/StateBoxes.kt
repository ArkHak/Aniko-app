package com.aniko.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Индикатор загрузки. В отличие от старого `AnixLoadingBox` (Фаза 2), НЕ занимает весь экран
 * сам по себе — размер и позиционирование целиком отдаются вызывающей стороне через [modifier].
 * Это то, чего не хватало Фазе 5: `ReleaseDetailsScreen` была вынуждена обходить фиксированный
 * `fillMaxSize()` старой версии вручную через голый `CircularProgressIndicator` для секции
 * внутри прокручиваемой колонки (см. `ReleaseDetailsScreen.kt`).
 */
@Composable
fun AnixLoadingState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Состояние ошибки с опциональной кнопкой повтора. См. [AnixLoadingState] — размер не навязан. */
@Composable
fun AnixErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val dimens = AnixThemeTokens.dimens
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
            modifier = Modifier.padding(dimens.spaceL),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                // Подтверждено на устройстве (Фаза 11, T9): M3 Button не сливает свой Text{} в
                // озвучиваемый узел — общий для всего приложения компонент, чинится один раз тут.
                val retryLabel = LocalStrings.current.commonRetry
                Button(
                    onClick = onRetry,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = retryLabel },
                ) { Text(retryLabel) }
            }
        }
    }
}

/** Пустое состояние с опциональным действием. См. [AnixLoadingState] — размер не навязан. */
@Composable
fun AnixEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val dimens = AnixThemeTokens.dimens
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
            modifier = Modifier.padding(dimens.spaceL),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = actionLabel },
                ) { Text(actionLabel) }
            }
        }
    }
}

/**
 * Схлопывает ветвление loading/error/empty/data в одно место — сейчас это ветвление
 * продублировано вручную в HomeScreen/LibraryScreen/SearchScreen/ProfileScreen/ScheduleScreen/
 * ReleaseDetailsScreen (composeApp), P6.T6/треки Фазы 7-9 смогут на неё опереться.
 */
@Composable
fun <T> AnixContentSlot(
    state: AnixContentState<T>,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    content: @Composable (List<T>) -> Unit,
) {
    when {
        state.isLoading && state.items.isEmpty() -> AnixLoadingState(modifier)
        state.errorMessage != null && state.items.isEmpty() -> AnixErrorState(state.errorMessage, modifier, onRetry)
        state.isEmpty && emptyMessage != null -> AnixEmptyState(emptyMessage, modifier)
        else -> content(state.items)
    }
}

@Deprecated(
    "Use AnixLoadingState — it does not fill the whole screen on its own",
    ReplaceWith("AnixLoadingState(modifier.fillMaxSize())"),
)
@Composable
fun AnixLoadingBox(modifier: Modifier = Modifier) = AnixLoadingState(modifier.fillMaxSize())

@Deprecated(
    "Use AnixErrorState",
    ReplaceWith("AnixErrorState(message, modifier.fillMaxSize(), onRetry)"),
)
@Composable
fun AnixErrorBox(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) = AnixErrorState(message, modifier.fillMaxSize(), onRetry)

@Deprecated(
    "Use AnixEmptyState",
    ReplaceWith("AnixEmptyState(message, modifier.fillMaxSize())"),
)
@Composable
fun AnixEmptyBox(
    message: String,
    modifier: Modifier = Modifier,
) = AnixEmptyState(message, modifier.fillMaxSize())
