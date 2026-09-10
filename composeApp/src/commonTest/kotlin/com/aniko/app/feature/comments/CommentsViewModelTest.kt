package com.aniko.app.feature.comments

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Клиентская валидация длины черновика комментария (P16.T17, `CommentComposer`/`submitComment`).
 * Тест на чистую функцию, а не на `CommentsViewModel` целиком: единственное место, где реально
 * возможна ошибка — граница длины, не сеть/пагинатор (те уже покрыты паттерном `vote`/`refresh`
 * остального `CommentsViewModel`, здесь ничего нового не добавлено).
 */
class CommentsViewModelTest {
    @Test
    fun blankMessageIsInvalid() {
        assertFalse(isCommentMessageValid(""))
        assertFalse(isCommentMessageValid("   "))
    }

    @Test
    fun singleCharacterMessageIsInvalid() {
        assertFalse(isCommentMessageValid("a"))
    }

    @Test
    fun messageIsTrimmedBeforeLengthCheck() {
        // Пробелы по краям не должны засчитываться в длину — иначе "  a  " (5 символов) прошёл бы
        // валидацию, хотя после отправки на сервер (`message.trim()` в `submitComment`) это тот же
        // самый однобуквенный мусор, что и в [singleCharacterMessageIsInvalid].
        assertFalse(isCommentMessageValid("  a  "))
    }

    @Test
    fun twoCharacterMessageIsValid() {
        assertTrue(isCommentMessageValid("ok"))
    }

    @Test
    fun ordinaryMessageIsValid() {
        assertTrue(isCommentMessageValid("Отличная серия, жду продолжения!"))
    }
}
