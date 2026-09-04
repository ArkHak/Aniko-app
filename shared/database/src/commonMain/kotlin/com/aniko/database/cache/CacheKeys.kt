package com.aniko.database.cache

/**
 * Ключи записей кэша листингов (`ReleaseListStore`), P4.T1/P4.T3.
 *
 * Схема ключа — `<namespace>:<params...>:<page>`, всегда с номером страницы последним сегментом.
 * Это специально согласовано с [listPrefix]: отбросив последний `:<page>`-сегмент, получаем общий
 * префикс всех страниц одного и того же листинга — по нему стор одним `LIKE`-запросом чистит/
 * инвалидирует сразу весь листинг (например, все страницы «Смотрю» одним DELETE), не обходя
 * каждую страницу отдельно.
 */
object CacheKeys {
    fun watching(page: Int): String = "watching:$page"

    fun myList(
        statusApiValue: Int,
        page: Int,
    ): String = "myList:$statusApiValue:$page"

    fun favorites(page: Int): String = "favorites:$page"

    fun history(page: Int): String = "history:$page"

    fun schedule(weekDay: Int): String = "schedule:$weekDay"

    /**
     * Общий префикс всех страниц листинга, к которому принадлежит [key] (сам [key] тоже
     * произведён одной из функций выше). Используется для инвалидации/удаления листинга целиком.
     */
    fun listPrefix(key: String): String = "${key.substringBeforeLast(':')}:"
}
