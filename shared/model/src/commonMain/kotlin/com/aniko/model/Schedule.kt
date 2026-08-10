package com.aniko.model

/**
 * День недели в терминах Anixart-расписания (`GET schedule`).
 *
 * Не переиспользуем `kotlinx.datetime.DayOfWeek` — этой зависимости нет ни в `shared:model`,
 * ни в `shared:data` (проверено по `gradle/libs.versions.toml` и `build.gradle.kts` обоих
 * модулей на 2026-08-10), тащить её в граф зависимостей ради одного enum избыточно.
 */
enum class WeekDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

/**
 * Домен-модель `GET schedule` — расписание выхода эпизодов по дням недели.
 *
 * Проверено вживую 2026-08-10: эндпоинт публичный (без `token`), без параметров, отдаёт
 * `code: 0` и 7 массивов полных `Release` под ключами `monday..sunday`. Точного времени
 * выхода серии в ответе нет — только группировка по дню недели (задокументированное
 * ограничение, см. `docs/REELWAVE_PLAN.md`, раздел Schedule).
 *
 * [byDay] всегда содержит все 7 дней (пустой список, если на день ничего не запланировано),
 * порядок ключей — понедельник → воскресенье, как в самом ответе API.
 */
data class Schedule(
    val byDay: Map<WeekDay, List<Release>>,
) {
    /** Релизы конкретного дня; пустой список, если на этот день ничего не запланировано. */
    fun releasesOn(day: WeekDay): List<Release> = byDay[day].orEmpty()
}
