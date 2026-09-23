package com.aniko.data.dto

import kotlinx.serialization.Serializable

/**
 * Одна легальная стриминг-площадка, на которой релиз официально доступен
 * (`GET release/streaming/platform/{releaseId}`, `PageableResponseDto<ReleaseStreamingPlatformDto>.content`).
 *
 * Сверено вживую (2026-09-23, curl `https://api-s.anixsekai.com/...`, НЕ из РФ — механизм не
 * завязан на гео-локацию запроса, сервер отдаёт список независимо от того, откуда пришёл запрос):
 * - `GET release/streaming/platform/20257` → одна площадка (`Кинопоиск`).
 * - `GET release/streaming/platform/19346` → несколько площадок (`Кинопоиск`, `Иви`), с
 *   разными [position].
 * - `GET release/streaming/platform/20223` → `content: []` — у релиза легальных площадок нет,
 *   обычная пустая страница, не ошибка.
 *
 * Форма ответа — обычная пагинируемая обёртка Anixart (`{code, content, current_page,
 * total_page_count, total_count}`), 1:1 совпадающая с уже существующим [PageableResponseDto] —
 * отдельного DTO-обёртки под этот эндпоинт не потребовалось.
 *
 * Найдено статически в decompiled APK Anixart v10.0 (`ReleaseStreamingPlatformUiController`,
 * связанная модель площадки) — поля [id]/[name]/[icon]/[url]/[position] без `@JsonProperty`,
 * значит совпадают с именем свойства as-is (тот же вывод, что и у [NamedRefDto]).
 */
@Serializable
data class ReleaseStreamingPlatformDto(
    val id: Long = 0,
    val name: String = "",
    val icon: String? = null,
    val url: String = "",
    val position: Int = 0,
)
