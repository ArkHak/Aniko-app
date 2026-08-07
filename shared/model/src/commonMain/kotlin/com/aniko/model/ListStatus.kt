package com.aniko.model

/**
 * Статус релиза в списке пользователя.
 *
 * Числовые значения — те же, что использует Anixart API в путях вида
 * `profile/list/add/{status}/{r_id}` и `profile/list/all/{status}/{page}`.
 * Расшифровка взята из `docs/api/ENDPOINTS.md` (секция «Статусы списков»).
 */
enum class ListStatus(
    val apiValue: Int,
) {
    /** «Смотрю» — `status_watching`. */
    WATCHING(1),

    /** «В планах» — `status_plan`. */
    PLANNED(2),

    /** «Просмотрено» — `status_completed`. */
    COMPLETED(3),

    /** «Отложено» — `status_hold_on`. */
    ON_HOLD(4),

    /** «Брошено» — `status_dropped`. */
    DROPPED(5),
    ;

    companion object {
        /** Возвращает статус по значению из API либо `null`, если релиз не в списке. */
        fun fromApiValue(apiValue: Int?): ListStatus? = entries.firstOrNull { it.apiValue == apiValue }
    }
}
