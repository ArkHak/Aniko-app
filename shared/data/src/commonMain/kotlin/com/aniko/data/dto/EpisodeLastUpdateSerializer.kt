package com.aniko.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * [ReleaseDto.episodeLastUpdate] — полиморфное поле API, встречается минимум в трёх формах:
 * - unix-timestamp числом (`1758026400`) — форма из сэмплов `docs/api/samples/`;
 * - `null` — релиз без серий/обновлений;
 * - объектом `{"last_episode_update_date": 1758026400, ...}` — живой запуск 2026-09-17 (Desktop,
 *   релиз id 20236 «Чёрный факел», `GET release/{id}?extended_mode=true`) поймал её крашем
 *   `JsonConvertException: Expected numeric literal at path: $.release.episode_last_update`,
 *   который ронял парсинг ВСЕЙ расширенной карточки релиза («Не удалось загрузить дополнительную
 *   информацию» на иначе рабочем экране). Тот же класс полиморфного поля, что и у
 *   [LastViewEpisodeSerializer], — сервер использует один JSON-ключ в разных формах, и ни один
 *   decompiled-источник это не описывает.
 *
 * Объектная форма читается по известному ключу `last_episode_update_date` (число или quoted-число);
 * неизвестные формы деградируют в `null` — поле опциональное (бейдж «новая серия», см.
 * `Release.episodeLastUpdate`), карточка при этом остаётся рабочей.
 */
internal object EpisodeLastUpdateSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EpisodeLastUpdate", PrimitiveKind.LONG)

    override fun serialize(
        encoder: Encoder,
        value: Long?,
    ) {
        // `Encoder.encodeNull()` помечен ExperimentalSerializationApi — для null кладём JsonNull
        // через JsonEncoder (эти DTO всё равно проходят только через Json, см. deserialize).
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonPrimitive(value))
        } else if (value != null) {
            encoder.encodeLong(value)
        }
    }

    override fun deserialize(decoder: Decoder): Long? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.longOrNull ?: element.content.toLongOrNull()
            is JsonObject -> (element[UPDATE_DATE_FIELD] as? JsonPrimitive)?.toLongLenient()
            else -> null
        }
    }

    private fun JsonPrimitive.toLongLenient(): Long? = longOrNull ?: content.toLongOrNull()

    private const val UPDATE_DATE_FIELD = "last_episode_update_date"
}
