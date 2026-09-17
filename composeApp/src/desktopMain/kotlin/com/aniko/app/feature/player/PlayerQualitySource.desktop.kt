package com.aniko.app.feature.player

/**
 * См. KDoc [isEmbedQualityControllerDriven] в commonMain — Desktop: качества приходят ТОЛЬКО от
 * `EmbedVideoController` (резолвер прозондировал каждое), доменный фолбэк не используется.
 */
actual fun isEmbedQualityControllerDriven(): Boolean = true
