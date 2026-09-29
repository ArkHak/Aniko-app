package com.aniko.app.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
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
     * таб-бара, а этот маршрут — дочерний экран, открываемый шестерёнкой (на Compact/Medium — из
     * `TopAppBar` `ProfileScreen.kt`, на Expanded — из футера сайдбара, `SidebarChromeActions` в
     * `App.kt`). Переключатель темы отсюда переехал на [Profile] (мокап рисует его прямо
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
     * Список уведомлений в приложении (P16.T18), открывается из колокольчика — в `TopAppBar`
     * `ProfileScreen.kt` на Compact/Medium, в футере сайдбара (`SidebarChromeActions`, `App.kt`)
     * на Expanded. Своя лента, а не параметр [Profile]: своя пагинация
     * ([com.aniko.data.repository.NotificationRepository]), тот же архитектурный выбор, что у
     * [ReleaseComments] относительно `ReleaseDetails`.
     */
    @Serializable
    data object Notifications : AnixDestination

    /**
     * Лента постов каналов/блогов (P16.T3, MVP), открывается с плитки "Лента" в
     * `HomeQuickActions`. Read-only список, без открытия отдельной статьи/постинга (см. KDoc
     * `FeedScreen`) — поэтому маршрут без параметров, как [Notifications].
     */
    @Serializable
    data object Feed : AnixDestination

    /**
     * Публичные коллекции (P16.T16, MVP), открывается с плитки "Коллекции" в `HomeQuickActions`.
     * Read-only список, без открытия деталей коллекции (см. KDoc `CollectionsScreen`) — тот же
     * приём, что у [Feed].
     */
    @Serializable
    data object Collections : AnixDestination

    /**
     * Экран-галерея дизайн-токенов (Фаза 2 плана, P2.T12): палитра/типографика/spacing/radius
     * с переключателем языка и темы для визуальной проверки. Debug-маршрут вне основной табовой
     * навигации намеренно, не часть продуктового флоу. Раньше открывался пунктом «Дизайн-токены»
     * из [Settings]; пункт убран по запросу пользователя (2026-09-21), маршрут и экран оставлены
     * в коде, но из UI недостижимы.
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
     * @param pendingVoiceTypeId — необязательный "предвыбери эту озвучку", заполняется ТОЛЬКО
     * парсером deep link (`aniko://release/{id}?voice={typeId}`, шеринг «верхней любимой
     * озвучки», см. `topFavoriteVoiceType` в `ReleaseDetailsContract.kt`). Это НЕ прямой вход в
     * [Player] и не автозапуск: карточка только выбирает чип озвучки, когда список типов
     * загрузится (`ReleaseDetailsScreen.HandlePendingVoiceTypeDeepLink`) — источники/серии доигры
     * подгружаются по обычному флоу выбора пользователем. Битый/отсутствующий в списке `typeId`
     * молча игнорируется (деградация до обычной карточки, см. KDoc `DeepLink.kt`).
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
        val pendingVoiceTypeId: Int? = null,
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

/**
 * Единственный корректный способ попасть на одну из пяти "корневых" секций таб-бара
 * ([AnixDestination.Home]/`.Search`/`.Library`/`.Schedule`/`.Profile`) — используется и
 * `onItemClick` таб-бара (`AnixAppScaffold` в `App.kt`), и быстрыми ссылками с самого Home
 * (`listSectionRoutes` в `App.kt`: "Открыть: Каталог"/"Расписание"/"Библиотека").
 *
 * Найдено живой инструментацией (`println`/`adb logcat` вокруг `navigate()`, воспроизведено
 * детерминированно на эмуляторе через 2 тапа от чистого старта): раньше три быстрые ссылки на
 * `HomeScreen` вызывали `navController.navigate(AnixDestination.X)` НАПРЯМУЮ, без опций —
 * рецепт `popUpTo(startDestinationId) { saveState = true } / launchSingleTop = true /
 * restoreState = true` был только в `onItemClick` таб-бара. Как только целевая вкладка была
 * достигнута ОДИН РАЗ в обход этого рецепта (например, тапом по карточке "Открыть: Каталог" на
 * Home, а не по вкладке "Каталог" таб-бара), последующий `navigate()` с полным рецептом из
 * `onItemClick` — ЛЮБОЙ, включая тап по "Главная" — превращался в полный no-op: ни
 * `currentDestination`, ни `currentBackStack` не менялись вообще (залогировано побайтово
 * идентичными до/после). Это и есть баг "тап по Главная не работает"/"открывает не то" —
 * `NavController` совместим только с ОДНИМ путём в back stack для каждой корневой секции;
 * смешение "чистого" `navigate()` и `navigate()` с этим рецептом на одну и ту же секцию ломает
 * его внутренний учёт `saveState`/`restoreState`. Фикс — маршрутизировать ЛЮБОЙ переход на
 * корневую секцию через одну и ту же функцию с одним и тем же рецептом, независимо от того, кто
 * его инициирует — таб-бар или быстрая ссылка с экрана.
 *
 * Хром-экраны [AnixDestination.Settings]/[AnixDestination.NotificationSettings]/
 * [AnixDestination.Notifications] открываются поверх вкладок голым `navigate()` (шестерёнка и
 * колокольчик в сайдбаре на Expanded, топбар профиля на Compact/Medium) и к конкретной вкладке не
 * привязаны. Тап по любой вкладке должен их закрывать: если не снять их ДО рецепта ниже,
 * `popUpTo(start) { saveState = true }` сохранит их в состояние вкладки, а `restoreState`
 * воскресит поверх её корня при возврате — настройки "висели" над вкладкой, пока их не закроют
 * кнопкой "назад" (баг 2026-09-29). `popBackStack(..., inclusive = true)` без `saveState` снимает
 * сам хром-экран и всё, что открыто поверх него (NotificationSettings поверх Settings), не
 * загрязняя сохранённое состояние вкладок. Контентный drill-down (ReleaseDetails и т.п.) не
 * трогаем — его сохранение между вкладками и есть смысл рецепта.
 */
fun NavHostController.navigateToTabRoot(destination: AnixDestination) {
    popBackStack(AnixDestination.Settings::class, inclusive = true)
    popBackStack(AnixDestination.Notifications::class, inclusive = true)
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
