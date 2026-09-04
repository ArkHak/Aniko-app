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

    /**
     * Экран настроек — Design Gallery, уведомления, язык, выход из аккаунта.
     *
     * До P13.T2 был вкладкой таб-бара (см. [AnixSection]) и сам открывал [Profile] пунктом «Мой
     * профиль». После сверки с мокапом Claude Design поток развернулся: [Profile] стал вкладкой
     * таб-бара, а этот маршрут — дочерний экран, открываемый шестерёнкой из `TopAppBar`
     * `ProfileScreen.kt`. Переключатель темы отсюда переехал на [Profile] (мокап рисует его прямо
     * под шапкой профиля); переключатель языка остался здесь.
     */
    @Serializable
    data object Settings : AnixDestination

    /**
     * Профиль текущего пользователя (Фаза 7).
     *
     * До P13.T2 открывался только из [Settings] («Мой профиль»). После сверки с мокапом Claude
     * Design — прямая вкладка таб-бара ([AnixSection.Profile], person-иконка), а [Settings] теперь
     * открывается ИЗ него (шестерёнка в `TopAppBar`), а не наоборот.
     */
    @Serializable
    data object Profile : AnixDestination

    /** Тумблеры подписки на уведомления (Фаза 10, P10.T6), открывается из [Settings]. */
    @Serializable
    data object NotificationSettings : AnixDestination

    /**
     * Экран-галерея дизайн-токенов (Фаза 2 плана, P2.T12): палитра/типографика/spacing/radius
     * с переключателем языка и темы для визуальной проверки. Debug-маршрут, открывается из
     * [Settings] — вне основной табовой навигации намеренно, не часть продуктового флоу.
     */
    @Serializable
    data object TokenGallery : AnixDestination

    /**
     * Карточка релиза.
     *
     * @param pendingEpisodeSourceId / [pendingEpisodePosition] — необязательная пара "хочу сразу
     * открыть эту серию", заполняется ТОЛЬКО парсером deep link (см. `DeepLink.kt`,
     * `parseDeepLink`) — обычная навигация из UI (`TitleNavigator.openTitle`) их не передаёт.
     * `null`/`null` по умолчанию, чтобы не задеть все существующие вызовы `ReleaseDetails(id)`.
     *
     * Deep link на эпизод (`aniko://release/{id}/episode/{sourceId}/{position}`) НЕ мапится сразу
     * в [Player]: `hostKey` ([Player.hostKey]) — клиентская классификация видеохоста
     * (`EpisodeSource.host`), которая приходит только вместе со списком источников конкретного
     * типа озвучки, а сам тип озвучки (`typeId`) в URL не кодируется (см. обоснование схемы в
     * `DeepLink.kt`). Поэтому deep link ведёт на карточку тайтла с этой парой параметров —
     * `ReleaseDetailsScreen` сам резолвит `hostKey` цепочкой типы→источники→серии
     * (`ReleaseDetailsViewModel.resolveDeepLinkEpisode`) и доходит до [Player], когда данные
     * загрузятся, а не сразу.
     */
    @Serializable
    data class ReleaseDetails(
        val releaseId: Int,
        val pendingEpisodeSourceId: Int? = null,
        val pendingEpisodePosition: Int? = null,
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

    /** Расписание выхода эпизодов по дням недели (Фаза 3: ScheduleApi, Фаза 4: ScheduleRepository). */
    @Serializable
    data object Schedule : AnixDestination

    /** Комментарии к релизу — отдельный маршрут (не параметр ReleaseDetails), см. журнал Фазы 5:
     * своя пагинация/сортировка (ReleaseCommentApi), не раздувает ReleaseDetailsUiState, и на
     * wide-экранах открывается как отдельный элемент pane-стека поверх Details. */
    @Serializable
    data class ReleaseComments(
        val releaseId: Int,
    ) : AnixDestination
}
