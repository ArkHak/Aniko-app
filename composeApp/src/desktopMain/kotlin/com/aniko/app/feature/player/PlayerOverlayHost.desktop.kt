package com.aniko.app.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.aniko.player.trackScreenBounds

/**
 * Desktop-actual — см. KDoc [PlayerOverlayHost] (commonMain) за архитектурным обоснованием.
 *
 * Плейсхолдер в главном окне ([Box] с [trackScreenBounds]) существует РОВНО ради измерения — где
 * на экране сейчас находится видео-область (та же область, что занимал бы [content], не будь этот
 * host desktop-specific): `trackScreenBounds` пересчитывает её и на изменения layout (анимация
 * `videoHeight` compact↔fullscreen), и на перемещение/ресайз самого окна приложения. Сам плейсхолдер
 * ничего не рисует — реальный [content] переезжает в отдельное окно ниже.
 *
 * Пока первых баунд ещё нет (`bounds == null`, до первого layout-прохода) — окно не создаётся
 * вовсе, лучше короткая пустота на старте экрана, чем окно с бессмысленными нулевыми координатами.
 */
@Composable
actual fun PlayerOverlayHost(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Box(modifier = modifier.trackScreenBounds { bounds = it })

    val currentBounds = bounds ?: return
    val density = LocalDensity.current
    // `remember(Unit)` с размером/позицией УЖЕ посчитанными из первого известного `currentBounds`,
    // а НЕ `remember { WindowState() }` (пустой конструктор → дефолтные 800×600) с последующей
    // мутацией в `LaunchedEffect` ниже — живая проверка (отчёт задачи, реальный эпизод) нашла
    // настоящую причину сплошного чёрного экрана: `LaunchedEffect` — асинхронный эффект, он
    // применяется ПОСЛЕ того, как `Window(state = windowState)` уже скомпоновался и AWT создал
    // нативный peer на дефолтных 800×600 (подтверждено диагностикой: `window.size` на первом кадре
    // — `[1,1]`, на втором — уже `[800,600]`, но `windowState.size` так и остаётся `800.0.dp x
    // 600.0.dp`, будто присвоение размера в эффекте вообще не подействовало, хотя позиция теми же
    // строками применяется исправно). Skia-поверхность этого окна инициализируется под тот самый
    // первый (дефолтный/вырожденный) размер и не переинициализируется в ответ на последующую мутацию
    // `windowState.size` — результат: окно физически остаётся на экране, но его Skia-кадр никогда не
    // презентуется валидным содержимым (подтверждено отдельно: даже хардкодный непрозрачный красный
    // фон вместо `content` рисовался тем же сплошным чёрным — не вопрос прозрачности, а вопрос
    // "кадр этой поверхности никогда не публикуется"). Конструирование `WindowState` сразу с верным
    // размером/позицией (тот же расчёт, что раньше был в эффекте) убирает сам вырожденный первый
    // кадр — Skia-поверхность с самого начала создаётся под реальный размер видео-области.
    // `remember { ... }` БЕЗ ключа (не `remember(currentBounds)`!) — инициализатор обязан
    // выполниться РОВНО ОДИН РАЗ, беря то `currentBounds`, что первым дошло сюда (return-guard
    // выше гарантирует, что это уже реальное значение, не null); ключом по `currentBounds` этот
    // `remember` пересоздавал бы `WindowState` (а с ним и реальное AWT-окно, см. `Window(state =
    // windowState)` ниже) на КАЖДОЕ изменение границ — включая каждый промежуточный кадр анимации
    // `videoHeight` compact↔fullscreen (P13) — то самое дребезжание/пересоздание окна на каждый тик
    // анимации, которого весь этот механизм (эффект ниже, не пересоздание) как раз избегает.
    val windowState =
        remember {
            with(density) {
                WindowState(
                    position = WindowPosition(currentBounds.left.toDp(), currentBounds.top.toDp()),
                    size = DpSize(currentBounds.width.toDp(), currentBounds.height.toDp()),
                )
            }
        }
    // Последующие изменения границ (та же анимация `videoHeight`) — окно уже существует, здесь
    // `windowState.size`/`.position` мутируются как обычно, без вырожденного первого кадра эта
    // мутация применяется штатно (см. KDoc `windowState` выше).
    LaunchedEffect(currentBounds) {
        with(density) {
            windowState.position = WindowPosition(currentBounds.left.toDp(), currentBounds.top.toDp())
            windowState.size = DpSize(currentBounds.width.toDp(), currentBounds.height.toDp())
        }
    }

    Window(
        onCloseRequest = {},
        state = windowState,
        undecorated = true,
        resizable = false,
        transparent = true,
        // `true` (по умолчанию) — контролам (play/pause, progress bar, тап-зоны) нужны реальные
        // клики. Живая проверка (см. отчёт задачи) — не привёл ли фокус этого окна к тому, что
        // `PlayerKeyboardShortcuts.desktop.kt` (слушает главное окно) перестаёт получать
        // клавиатуру после клика по оверлею: если да, следующий шаг — `onPreviewKeyEvent` здесь,
        // перекидывающий события туда же, куда шлёт главное окно.
        focusable = true,
        // Должно быть выше видео-окна (`EmbedPlayer.desktop.kt`) — оба `alwaysOnTop`, порядок
        // между двумя такими окнами не гарантирован одним этим флагом, поэтому досылаем `toFront()`
        // на каждое изменение границ (видео-окно пересоздаёт/двигает себя в ответ на тот же сигнал).
        alwaysOnTop = true,
    ) {
        LaunchedEffect(currentBounds) { window.toFront() }
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}
