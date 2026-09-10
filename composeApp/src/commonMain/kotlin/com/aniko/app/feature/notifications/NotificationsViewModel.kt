package com.aniko.app.feature.notifications

import androidx.lifecycle.viewModelScope
import com.aniko.app.mvi.BaseViewModel
import com.aniko.data.paging.Paginator
import com.aniko.data.repository.NotificationRepository
import com.aniko.model.AppNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel экрана уведомлений (P16.T18) — пагинация через
 * `NotificationRepository.notificationsPaginator`, тот же MVI-каркас, что `CommentsViewModel`
 * (Фаза 7, P7.T12), без сортировки/голосования — их здесь просто нет.
 *
 * [load] дополнительно отмечает бейдж непрочитанных увиденным
 * ([NotificationRepository.acknowledgeSeen]) — единственное место в приложении, где это
 * вызывается, ровно один раз за открытие экрана (см. [started]).
 */
class NotificationsViewModel(
    private val notificationRepository: NotificationRepository,
) : BaseViewModel<NotificationsState, NotificationsIntent, NotificationsEffect>(initialState = NotificationsState()) {
    private var paginator: Paginator<AppNotification>? = null
    private var pagingCollectJob: Job? = null
    private var started = false

    override suspend fun handleIntent(intent: NotificationsIntent) {
        when (intent) {
            NotificationsIntent.Load -> load()
            NotificationsIntent.LoadMore -> loadNextAndReportIfMoreFailed()
            NotificationsIntent.Retry -> paginator?.refresh()
        }
    }

    private suspend fun load() {
        if (started) return
        started = true
        startPaginator()
        acknowledgeSeen()
    }

    private suspend fun startPaginator() {
        pagingCollectJob?.cancel()
        val newPaginator = notificationRepository.notificationsPaginator()
        paginator = newPaginator
        pagingCollectJob =
            newPaginator.state
                .onEach { paging -> updateState { copy(paging = paging) } }
                .launchIn(viewModelScope)
        newPaginator.loadNext()
    }

    /**
     * Фоновая, необязательная для успеха экрана операция — отдельный `launch`, а не часть
     * [startPaginator]: список уже показывается независимо от того, удался ли `notification/count`
     * для бейджа. `TooGenericExceptionCaught`/`SwallowedException`: неудача здесь означает только
     * "бейдж не спрячется до следующего открытия экрана", не ошибку списка, поэтому неважен
     * конкретный тип — тот же приём, что `NotificationPoller.pollSafely`.
     * [CancellationException] пробрасывается явно, чтобы отмена `viewModelScope` не глушилась.
     */
    private fun acknowledgeSeen() {
        viewModelScope.launch {
            try {
                notificationRepository.acknowledgeSeen()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception,
            ) {
                // Намеренно проглочено, см. KDoc выше.
            }
        }
    }

    /** См. KDoc `CommentsViewModel.loadNextAndReportIfMoreFailed` — тот же смысл: подгрузка
     *  следующей страницы уже непустого списка отдельно сигналит об ошибке эффектом. */
    private suspend fun loadNextAndReportIfMoreFailed() {
        val currentPaginator = paginator ?: return
        currentPaginator.loadNext()
        val pagingState = currentPaginator.state.value
        val error = pagingState.error
        if (error != null && pagingState.items.isNotEmpty()) {
            emitEffect(NotificationsEffect.ShowError(error))
        }
    }
}
