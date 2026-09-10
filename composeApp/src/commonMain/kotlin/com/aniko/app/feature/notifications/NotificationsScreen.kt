package com.aniko.app.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.mvi.CollectEffects
import com.aniko.app.notification.notificationTitleAndBody
import com.aniko.app.ui.toContentState
import com.aniko.model.AnixError
import com.aniko.model.AppNotification
import com.aniko.model.AppNotificationKind
import com.aniko.ui.component.AnixContentSlot
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран уведомлений в приложении (P16.T18) — открывается колокольчиком из `TopAppBar`
 * `ProfileScreen.kt`. Пагинация — `NotificationRepository.notificationsPaginator`
 * (`GET notification/all/{page}`), тот же каркас `AnixContentSlot`/prefetch, что
 * `ReleaseCommentsScreen` (Фаза 7, P7.T12).
 *
 * Строки заголовка/текста каждой строки — [notificationTitleAndBody], та же функция, что уже
 * формирует текст локальных OS-уведомлений ([com.aniko.app.notification.AppNotificationContentFactory]):
 * одно и то же событие читается одинаково что в шторке ОС, что здесь.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onReleaseClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.dispatch(NotificationsIntent.Load) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    // См. `ReleaseCommentsScreen`/`HomeScreen` — тот же нерешённый пока случай: `SnackbarHostState`
    // сейчас приватен `AnixSessionGate` (App.kt) и не прокинут в feature-пакеты, эффект пока молча
    // игнорируется.
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is NotificationsEffect.ShowError -> Unit
        }
    }

    val contentState = state.paging.toContentState { error -> error.toDisplayMessage(strings) }

    Scaffold(
        modifier = modifier.testTag(AnixTestTags.NOTIFICATIONS_SCREEN_ROOT),
        topBar = {
            TopAppBar(
                title = { Text(strings.notificationsTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier =
                            Modifier.clearAndSetSemantics {
                                contentDescription = strings.backContentDescription
                            },
                    ) {
                        AnixIcon(name = "arrow_back", contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        AnixContentSlot(
            state = contentState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            emptyMessage = strings.notificationsEmpty,
            onRetry = { viewModel.dispatch(NotificationsIntent.Retry) },
        ) { notifications ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.spaceM, vertical = dimens.spaceS),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
            ) {
                itemsIndexed(notifications, key = { _, notification -> notification.id }) { index, notification ->
                    if (index >= notifications.size - NOTIFICATIONS_PREFETCH_THRESHOLD) {
                        viewModel.dispatch(NotificationsIntent.LoadMore)
                    }
                    NotificationRow(
                        notification = notification,
                        onClick = { notification.releaseId?.let(onReleaseClick) },
                    )
                }
            }
        }
    }
}

/**
 * Строка одного уведомления: кружок-иконка категории ([iconName]) + заголовок/текст
 * ([notificationTitleAndBody]). Кликабельна, только если есть [AppNotification.releaseId] —
 * `friend`/`article` без релиза не ведут никуда, а не открывают приложение впустую.
 */
@Composable
private fun NotificationRow(
    notification: AppNotification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    val (title, body) = notificationTitleAndBody(notification, strings)
    val clickable = notification.releaseId != null

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .let { if (clickable) it.clickable(onClick = onClick) else it }
                .padding(dimens.spaceS),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(dimens.space48)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AnixIcon(
                    name = notification.kind.iconName(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Иконка категории из существующего 31-глифового набора [com.aniko.ui.component.AnixIcon] (P16.T18
 * не расширяет шрифт ради каждого типа — только `notifications` для входной точки, см. журнал
 * плана): `COMMENT`/`ARTICLE` не имеют точного смыслового соответствия в наборе, подобраны
 * ближайшие нейтральные (не вводящие в заблуждение) — `visibility`/`grid_view`.
 */
private fun AppNotificationKind.iconName(): String =
    when (this) {
        AppNotificationKind.EPISODE -> "play_circle"
        AppNotificationKind.RELATED_RELEASE -> "video_library"
        AppNotificationKind.FRIEND -> "person"
        AppNotificationKind.COMMENT -> "visibility"
        AppNotificationKind.ARTICLE -> "grid_view"
        AppNotificationKind.UNKNOWN -> "help"
    }

/** P2.T10: `AnixError.message` — технический текст для логов, не для UI. Всегда локализованный
 *  generic-фолбэк, как у `ReleaseCommentsScreen`/`HomeScreen`. */
private fun AnixError.toDisplayMessage(strings: Strings): String = strings.notificationsLoadError

private const val NOTIFICATIONS_PREFETCH_THRESHOLD = 6
