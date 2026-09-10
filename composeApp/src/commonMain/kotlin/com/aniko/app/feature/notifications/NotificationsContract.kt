package com.aniko.app.feature.notifications

import com.aniko.app.mvi.UiEffect
import com.aniko.app.mvi.UiIntent
import com.aniko.app.mvi.UiState
import com.aniko.data.paging.PagingState
import com.aniko.model.AnixError
import com.aniko.model.AppNotification

/** Состояние экрана уведомлений (P16.T18) — один пагинированный список, без сортировки/фильтров:
 *  `GET notification/all/{page}` уже отдаёт все типы вперемешку, новые первыми (см. KDoc
 *  `NotificationApi.all`), заводить вкладки по типу не входит в объём задачи. */
data class NotificationsState(
    val paging: PagingState<AppNotification> = PagingState(),
) : UiState

/** [Load] идемпотентен — повторный вызов, пока список уже грузится/загружен, no-op (см.
 *  `NotificationsViewModel.load`), поэтому безопасно диспатчить из `LaunchedEffect(Unit)` при
 *  каждой рекомпозиции экрана. */
sealed interface NotificationsIntent : UiIntent {
    data object Load : NotificationsIntent

    data object LoadMore : NotificationsIntent

    data object Retry : NotificationsIntent
}

/** См. KDoc `CommentsEffect.ShowError` — тот же случай: неудачная подгрузка следующей страницы
 *  уже непустого списка (ошибка первой загрузки видна через `state.paging.error`/
 *  `AnixContentSlot` и отдельного эффекта не требует). */
sealed interface NotificationsEffect : UiEffect {
    data class ShowError(
        val error: AnixError,
    ) : NotificationsEffect
}
