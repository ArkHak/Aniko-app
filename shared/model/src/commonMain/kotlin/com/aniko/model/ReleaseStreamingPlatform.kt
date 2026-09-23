package com.aniko.model

/**
 * Легальная стриминг-площадка, на которой релиз официально доступен (Title Detail).
 *
 * Источник — `GET release/streaming/platform/{releaseId}` (см. `ReleaseStreamingPlatformDto` в
 * `shared/data`, сверено вживую 2026-09-23) — честно отражает то, что вернул API, без какой-либо
 * гео-логики на клиенте: сервер сам решает, что вернуть, приложению остаётся только отрисовать
 * список.
 */
data class ReleaseStreamingPlatform(
    val id: Long,
    val name: String,
    val iconUrl: String?,
    val url: String,
)
