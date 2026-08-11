package com.aniko.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Стек содержимого detail-панели на wide-экранах (P5.T3, трек B) — состояние ВНЕ `NavController`,
 * переживающее смену window size class (см. KDoc [DetailPaneRoute]).
 *
 * Инвариант, под который написана [open]: [DetailPaneRoute.Details] в стеке максимум один и всегда
 * на дне; [DetailPaneRoute.Comments] может лежать поверх него. Это гарантирует, что клики по разным
 * тайтлам в списке (list pane остаётся видимым рядом с detail pane в two-pane режиме, так что
 * повторные клики по разным карточкам вполне реальны) не растят стек бесконечно — иначе "назад" на
 * wide-экране пришлось бы жать много раз, чтобы вернуться к пустой панели.
 *
 * Известное упрощение (осознанно не блокирующее для Фазы 5): состояние держится в обычном
 * [remember], не [androidx.compose.runtime.saveable.rememberSaveable] — стек панели не переживёт
 * поворот экрана/пересоздание процесса (в отличие от `NavController`, у которого сохранение "из
 * коробки"). Для compact-экранов это не важно (там панель вообще не используется — см.
 * [TitleNavigator]), для wide — деградация всего лишь до "detail-панель опустела", не крэш.
 */
@Stable
class DetailPaneStack(
    initial: List<DetailPaneRoute> = emptyList(),
) {
    var entries: List<DetailPaneRoute> by mutableStateOf(initial)
        private set

    val top: DetailPaneRoute? get() = entries.lastOrNull()
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * - [DetailPaneRoute.Details] того же релиза, что уже лежит на дне стека (в т.ч. если поверх
     *   открыт [DetailPaneRoute.Comments]) — no-op, ничего не меняется.
     * - [DetailPaneRoute.Details] ДРУГОГО релиза — полный сброс стека до `[route]`: старый `Details`
     *   и любой `Comments` над ним относились к прошлому релизу и больше не актуальны.
     * - [DetailPaneRoute.Comments] — добавляется поверх `Details` того же релиза; повторный `open`
     *   тем же `Comments` (уже наверху) — no-op. Если на дне стека `Details` ДРУГОГО релиза или
     *   стек пуст (найдено ревью Фазы 5 — раньше это молча нарушало инвариант "Comments только
     *   поверх Details того же релиза"), сначала подставляется `Details` этого релиза — `Comments`
     *   никогда не остаётся в стеке без соответствующего `Details` под ним.
     */
    fun open(route: DetailPaneRoute) {
        entries =
            when (route) {
                is DetailPaneRoute.Details ->
                    if (entries.any { it is DetailPaneRoute.Details && it.releaseId == route.releaseId }) {
                        entries
                    } else {
                        listOf(route)
                    }

                is DetailPaneRoute.Comments -> {
                    val bottom = entries.firstOrNull() as? DetailPaneRoute.Details
                    when {
                        top == route -> entries
                        bottom?.releaseId == route.releaseId -> entries + route
                        else -> listOf(DetailPaneRoute.Details(route.releaseId), route)
                    }
                }
            }
    }

    /** Снимает верхний элемент стека. `true` — было что снимать, `false` — стек уже был пуст. */
    fun pop(): Boolean {
        if (entries.isEmpty()) return false
        entries = entries.dropLast(1)
        return true
    }

    fun clear() {
        entries = emptyList()
    }
}

@Composable
fun rememberDetailPaneStack(): DetailPaneStack = remember { DetailPaneStack() }
