package com.aniko.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * [ReleaseDto.lastViewEpisode] обычно — простое число (порядковый номер серии). Но в `history[]`
 * (`GET profile/{id}`, найдено живым тестом 2026-08-23, см. `docs/api/ANIXART_API.md`, открытый
 * вопрос №6) то же самое имя поля `last_view_episode` на верхнем уровне элемента истории приходит
 * ПОЛНЫМ объектом эпизода (`{"@id":...,"releaseId":...,"position":...,"release":{...}}`), а не
 * числом — тот же decompiled JSON-ключ используется в двух разных формах в зависимости от
 * контекста, ни в одном из decompiled-источников это не описано явно. Без этого сериализатора
 * `Int?` не мог распарсить объектную форму и ронял парсинг всего профиля целиком
 * (`JsonConvertException`) на любом аккаунте с непустой историей просмотра.
 *
 * Объектная форма не даёт достоверного номера серии (только `position`/`releaseId`, не сам номер
 * эпизода) — деградирует в `null`, а не выдумывает значение.
 */
internal object LastViewEpisodeSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LastViewEpisode", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: Int?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value)
        }
    }

    override fun deserialize(decoder: Decoder): Int? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()
        return (element as? JsonPrimitive)?.intOrNull
    }
}
