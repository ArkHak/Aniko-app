package com.anixkmp.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe маршруты навигации (androidx.navigation для Compose Multiplatform).
 * Добавлять сюда, а не разбрасывать строковые route по фичам.
 */
sealed interface AnixDestination {

    @Serializable
    data object Home : AnixDestination

    @Serializable
    data object Library : AnixDestination

    @Serializable
    data object Settings : AnixDestination

    /** Карточка релиза. */
    @Serializable
    data class ReleaseDetails(val releaseId: Int) : AnixDestination

    /** Плеер: релиз + выбранный источник + номер серии. */
    @Serializable
    data class Player(
        val releaseId: Int,
        val sourceId: Int,
        val position: Int,
    ) : AnixDestination
}
