package com.aniko.ui.component

import androidx.compose.runtime.Immutable

/**
 * UI-состояние списка/секции для компонентов Фазы 6 (P6.T6/T7) — НЕ то же самое, что
 * `com.aniko.data.paging.PagingState` из `:shared:data`: `shared/ui` зависит только от
 * `:shared:model` (см. `shared/ui/build.gradle.kts`), тянуть туда `:shared:data` ради одного
 * data-класса было бы инверсией слоёв. Мост `PagingState -> AnixContentState` живёт в
 * `composeApp` (см. `PagingStateAdapter.kt`), единственном месте, которое и так знает про оба.
 */
@Immutable
data class AnixContentState<out T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading && errorMessage == null
}
