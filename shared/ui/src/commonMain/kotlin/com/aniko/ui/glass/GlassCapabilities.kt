package com.aniko.ui.glass

/**
 * Единственная платформенная точка ветвления во всей фиче Liquid Glass (2026-09-11, feature/
 * liquid-glass-tab-bar). Всё остальное (двухслойный backdrop-blur через [androidx.compose.ui.
 * graphics.layer.GraphicsLayer], инвалидация через `Modifier.Node`) собирается из общего кода —
 * `Modifier.blur`/`renderEffect` реально размывает только там, где платформенный рендерер это
 * поддерживает:
 * - Android — `RenderEffect` (а значит и `BlurEffect`/`renderEffect` в Compose) работает только на
 *   API 31+ (`Build.VERSION_CODES.S`); ниже поле `renderEffect` просто молча игнорируется движком
 *   (без крэша, но и без блюра) — `minSdk` проекта 26, поэтому диапазон 26..30 обязан деградировать.
 * - iOS/Desktop (skiko-бэкенд) — `BlurEffect.isSupported()` истинно безусловно, блюр идёт через
 *   `ImageFilter.makeBlur` (Skia) на обеих платформах без версийных ограничений.
 *
 * [com.aniko.ui.glass.LiquidGlass] использует это как гейт: `false` → слой стекла деградирует в
 * непрозрачную заливку (`LiquidGlassStyle.fallbackAlpha`) вместо реального backdrop-blur.
 */
internal expect fun isRealtimeBlurSupported(): Boolean
