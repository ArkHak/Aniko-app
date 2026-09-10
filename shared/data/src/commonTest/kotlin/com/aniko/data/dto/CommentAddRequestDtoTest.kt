package com.aniko.data.dto

import com.aniko.network.AnixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `CommentAddRequestDto` — тело `POST release/comment/add/{releaseId}` (P16.T17,
 * `CommentRepository.addComment` → `ReleaseCommentsScreen` composer). Живьём это тело не
 * отправлялось в этой сессии (мутирующий эндпоинт, см. KDoc [CommentAddRequestDto]) — этот тест
 * страхует единственный реально рискованный момент из её KDoc: `message`/`spoiler` — camelCase-
 * имена БЕЗ `@SerialName`, в отличие от большинства остальных DTO в этом пакете (снейк-кейс через
 * `@SerialName`). Ошибка тут (например, случайно добавленный `@SerialName("message")`) сломала бы
 * публикацию молча — сервер просто не увидел бы текст сообщения.
 */
class CommentAddRequestDtoTest {
    @Test
    fun encodesMessage_withCamelCaseKeyAsIs() {
        val request = CommentAddRequestDto(message = "Отличная серия!")

        val json = AnixJson.encodeToString(CommentAddRequestDto.serializer(), request)

        val expectedKey = "\"message\":\"Отличная серия!\""
        assertTrue(json.contains(expectedKey), "ожидался camelCase-ключ \"message\", получено: $json")
    }

    @Test
    fun omitsNullOptionalFields_byDefault() {
        // AnixJson не переопределяет `explicitNulls`/`encodeDefaults` (см. KDoc
        // `FilterRequestDtoTest` — тот же принцип для другого RequestDto в этом пакете): черновик
        // top-level комментария без `parentCommentId`/`replyToProfileId` не должен слать эти ключи.
        val request = CommentAddRequestDto(message = "ok")

        val json = AnixJson.encodeToString(CommentAddRequestDto.serializer(), request)

        assertEquals("{\"message\":\"ok\"}", json)
    }
}
