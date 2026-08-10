package com.aniko.data.dto

import kotlinx.serialization.Serializable

/**
 * `GET schedule` — расписание выхода эпизодов по дням недели.
 *
 * Проверено вживую 2026-08-10: `curl -s 'https://api-s.anixsekai.com/schedule'` без токена и
 * без параметров вернул `HTTP 200` с `{"code": 0, "monday": [...], ..., "sunday": [...]}`.
 * Каждый день — массив ПОЛНЫХ объектов `Release` (те же поля, что и в `discover/watching`,
 * `release/{id}`), поэтому переиспользуется существующий [ReleaseDto].
 *
 * Статически подтверждено декомпилом: `ScheduleResponse.java` extends `Response` (значит есть
 * `code`), 7 полей `monday..sunday: List<Release>` с дефолтом `emptyList()`, без
 * `@JsonProperty` — имена полей уже lowercase день-недели, совпадают с JSON as-is.
 * `ScheduleApi.java` — интерфейс с одним методом `@GET("schedule") schedule()`, без параметров
 * вообще (ни `token`, ни пагинации).
 */
@Serializable
data class ScheduleResponseDto(
    override val code: Int = 0,
    val monday: List<ReleaseDto> = emptyList(),
    val tuesday: List<ReleaseDto> = emptyList(),
    val wednesday: List<ReleaseDto> = emptyList(),
    val thursday: List<ReleaseDto> = emptyList(),
    val friday: List<ReleaseDto> = emptyList(),
    val saturday: List<ReleaseDto> = emptyList(),
    val sunday: List<ReleaseDto> = emptyList(),
) : ApiCodeAware
