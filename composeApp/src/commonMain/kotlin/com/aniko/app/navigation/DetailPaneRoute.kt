package com.aniko.app.navigation

import kotlinx.serialization.Serializable

/**
 * Содержимое detail-панели вне `NavController` (P5.T3, трек B).
 *
 * На wide-экранах (`AnixWindowSize.isTwoPane`) detail-панель живёт своей жизнью рядом со списком —
 * её не обязательно (и не всегда правильно) выражать как `NavBackStackEntry`, потому что панель не
 * должна пропадать/пересоздаваться при смене size class на лету (поворот экрана, ресайз окна
 * desktop). Поэтому у неё свой лёгкий стек ([DetailPaneStack]), а не запись в `AnixDestination`
 * (те по-прежнему используются на compact-экранах как полноэкранные маршруты — см.
 * [TitleNavigator]).
 *
 * `@Serializable` здесь — не для навигации (в отличие от `AnixDestination`), а задел на будущее
 * сохранение состояния панели через `rememberSaveable`/`Saver`, если понадобится (см. KDoc
 * [DetailPaneStack] про текущее упрощение — `remember`, не `rememberSaveable`).
 */
@Serializable
sealed interface DetailPaneRoute {
    val releaseId: Int

    /** Карточка релиза — единственный элемент, который может лежать на дне стека панели. */
    @Serializable
    data class Details(
        override val releaseId: Int,
    ) : DetailPaneRoute

    /** Комментарии к релизу — пушится поверх уже открытого [Details] того же релиза. */
    @Serializable
    data class Comments(
        override val releaseId: Int,
    ) : DetailPaneRoute
}
