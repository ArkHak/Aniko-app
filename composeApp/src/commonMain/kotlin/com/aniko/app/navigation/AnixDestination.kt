package com.aniko.app.navigation

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

    /** Поиск релизов. */
    @Serializable
    data object Search : AnixDestination

    @Serializable
    data object Settings : AnixDestination

    /** Профиль текущего пользователя (Фаза 7), открывается из [Settings]. */
    @Serializable
    data object Profile : AnixDestination

    /** Карточка релиза. */
    @Serializable
    data class ReleaseDetails(
        val releaseId: Int,
    ) : AnixDestination

    /**
     * Плеер: релиз + выбранный источник + номер серии.
     *
     * `hostKey` ([com.aniko.model.VideoHost.key]) передаётся явно из экрана выбора источника,
     * а не вычисляется заново на экране плеера — так резолвинг хоста не зависит от
     * runtime-состояния другого репозитория/экрана (см. код-ревью Фазы 5: раньше `EpisodeRepository`
     * держал `lastSources` как мутабельный кэш специально для этого, что было гонкой состояния).
     */
    @Serializable
    data class Player(
        val releaseId: Int,
        val sourceId: Int,
        val position: Int,
        val hostKey: String,
    ) : AnixDestination
}
