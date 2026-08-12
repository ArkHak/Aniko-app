package com.aniko.app.ui

import com.aniko.data.paging.PagingState
import com.aniko.model.AnixError
import com.aniko.ui.component.AnixContentState

/**
 * Единственный переходник `:shared:data` -> `:shared:ui` (см. KDoc [AnixContentState] — зачем он
 * отдельный, а не `PagingState` напрямую). `isLoading || isRefreshing` — первичная и повторная
 * загрузка обе должны показывать [AnixContentState.isLoading] потребителю, который не различает
 * их (см. `AnixContentSlot`, `StateBoxes.kt`): при непустом списке контент всё равно отрисуется,
 * а для пустого — обе ветки означают «данных ещё нет, покажи индикатор».
 */
fun <T> PagingState<T>.toContentState(errorMessage: (AnixError) -> String): AnixContentState<T> =
    AnixContentState(
        items = items,
        isLoading = isLoading || isRefreshing,
        errorMessage = error?.let(errorMessage),
    )
