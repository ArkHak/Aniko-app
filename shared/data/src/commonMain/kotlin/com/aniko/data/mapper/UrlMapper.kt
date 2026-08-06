package com.aniko.data.mapper

/**
 * Достраивает относительный путь из API (например `image`/`avatar`) до абсолютного URL
 * относительно [base]. Вынесено из `ReleaseMapper.kt` в Фазе 7, т.к. понадобилось ещё и
 * `ProfileMapper.kt` — логика не изменена.
 */
internal fun String.toAbsoluteUrl(base: String): String =
    if (startsWith("http://") || startsWith("https://")) this else base.trimEnd('/') + "/" + trimStart('/')
