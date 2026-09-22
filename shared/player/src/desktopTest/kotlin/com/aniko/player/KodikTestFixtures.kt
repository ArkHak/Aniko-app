package com.aniko.player

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/*
 * Общие данные для юнит-тестов Kodik-резолвера: ответ `/ftor` (`links`) без сети.
 * Число в имени файла CDN берётся из лейбла качества, чтобы по URL было видно, чей он.
 */

/** URL качества [label] в том виде, в котором его отдаёт Kodik (progressive-mp4 на CDN Kodik). */
internal fun kodikCdnUrl(label: String): String {
    val height = label.filter(Char::isDigit)
    return "https://cloud.kodik-storage.com/useruploads/abc/$height.mp4"
}

/** Тот же URL после `preferPlayableUrl`: для CDN Kodik progressive-mp4 форсируется в HLS-манифест. */
internal fun kodikPlayableUrl(label: String): String = kodikCdnUrl(label) + ":hls:manifest.m3u8"

/** Поле `links` ответа `/ftor` с перечисленными ключами качеств — В ЭТОМ порядке, как отдал бы хост. */
internal fun kodikLinksOf(vararg labels: String): JsonObject =
    buildJsonObject {
        for (label in labels) {
            putJsonArray(label) { add(buildJsonObject { put("src", kodikCdnUrl(label)) }) }
        }
    }
