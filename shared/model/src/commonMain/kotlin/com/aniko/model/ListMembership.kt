package com.aniko.model

/**
 * Членство релиза в списках пользователя — снимок локальной строки `listMembership`
 * (`shared:database`), единственный источник правды для вкладок экрана «Мои списки».
 *
 * Держит только сами значения, без LWW-таймстампов `status_updated_at`/`favorite_updated_at`:
 * те — деталь записи (см. KDoc `ListMembership.sq`), читателю нужен лишь результат их разрешения.
 *
 * Отдельный тип, а не `Pair<ListStatus?, Boolean>` и не поля `Release`: `Release` — кэш ответа
 * сервера (его `myListStatus`/`isFavorite` устаревают в момент локальной мутации), а эта пара
 * полей живёт своей жизнью, меняется оптимистично даже офлайн и переживает перезапрос страницы.
 *
 * @property status статус в списке, `null` — релиз не числится ни в одном из [ListStatus].
 * @property isFavorite релиз в избранном.
 */
data class ListMembership(
    val status: ListStatus? = null,
    val isFavorite: Boolean = false,
)
