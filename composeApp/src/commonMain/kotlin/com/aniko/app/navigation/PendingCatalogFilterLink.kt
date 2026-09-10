package com.aniko.app.navigation

import com.aniko.model.CatalogFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Мост «deep link разобран» → «каталог применил фильтр» (P16.T2).
 *
 * Тот же приём и та же причина, что у [DeepLinkDispatcher]: ссылка приходит в момент, когда экран
 * каталога может быть ещё не создан (холодный старт) или уже создан (приложение открыто), а
 * навигация умеет только переключать секцию — состояние фильтра через неё не передать, не
 * превращая корневой маршрут секции в маршрут с аргументами (это сломало бы рецепт
 * `navigateToTabRoot` с `saveState`/`restoreState`, см. KDoc `AnixDestination`).
 *
 * `SearchViewModel` читает [current] при создании И подписывается на изменения (ссылка может
 * прийти на уже открытый каталог), после применения обязан вызвать [consume] — иначе тот же
 * фильтр применился бы повторно при следующем открытии каталога.
 */
object PendingCatalogFilterLink {
    private val pending = MutableStateFlow<CatalogFilter?>(null)

    val current: StateFlow<CatalogFilter?> = pending.asStateFlow()

    fun dispatch(filter: CatalogFilter) {
        pending.value = filter
    }

    fun consume() {
        pending.value = null
    }
}
