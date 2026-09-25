package com.aniko.player

import android.webkit.PermissionRequest

/**
 * Ресурсы `WebView`, которые страница embed-плеера вправе получить: только
 * [PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID] — без него `WebView` отклоняет запрос EME/Widevine,
 * который html5-плеер делает при инициализации видео (экран остаётся чёрным без ошибок в логе).
 *
 * Камера, микрофон и MIDI (`RESOURCE_VIDEO_CAPTURE`/`RESOURCE_AUDIO_CAPTURE`/`RESOURCE_MIDI_SYSEX`)
 * видеоплееру не нужны, а страница — чужая (Kodik, Sibnet и рекламные фреймы внутри них), поэтому
 * выдавать их по запросу нельзя (предрелизный аудит безопасности, issue #109).
 */
private val GRANTABLE_EMBED_RESOURCES = setOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)

/**
 * Можно ли выдать запрос страницы целиком: непустой и состоит только из [GRANTABLE_EMBED_RESOURCES].
 *
 * Всё или ничего — намеренно: если вместе с `PROTECTED_MEDIA_ID` страница просит ещё и микрофон,
 * запрос отклоняется целиком, а не выдаётся частично. Так поведение не зависит от того, как
 * конкретная версия `WebView` трактует `grant` подмножества запрошенных ресурсов.
 */
internal fun isEmbedPermissionGrantable(resources: Array<String>): Boolean =
    resources.isNotEmpty() && resources.all { it in GRANTABLE_EMBED_RESOURCES }
