package com.aniko.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.data.voicepin.LocalVoicePinStore
import com.aniko.model.VoiceType
import com.aniko.player.EmbedVideoController
import com.aniko.player.EmbedVideoState
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.theme.AppTheme
import com.russhwolf.settings.MapSettings
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Регрессия на центровку иконок в круглых кнопках/индикаторах оверлея плеера (жалоба
 * пользователя: «Назад», «Play/Pause», «перемотка назад/вперёд» смещены от центра круга).
 *
 * **Корневая причина** — в `AnixIcon` (`shared/ui`): глиф рисовался в слоте высотой 1em, а естественная
 * строка шрифта Material Symbols — 1.2em; Skia сжимала высоту строки до слота, но рисовала её от
 * верхнего края, и глиф оказывался на 0.1em НИЖЕ центра слота (2px у 24dp, 5.5px у 56dp; у нижней
 * навигации, `radio_button_unchecked` в кнопках и т.д. — тоже). Тест ловит это на реальных кнопках.
 *
 * Каждая кнопка рисуется офскрин ([runSkikoComposeUiTest], без реального VLCJ-плеера) при плотностях
 * 1x/2x/3x; на кадре находятся круг-подложка и видимые пиксели иконки (см. `IconCenteringMeasure.kt`).
 * Опорный центр — центр найденного по пикселям круга, а у кнопок без видимого круга (полноэкранный
 * оверлей: кружок там — состояние hover/ripple M3 `IconButton`) — центр границ узла семантики.
 *
 * **Метрика центра иконки.** bbox видимых пикселей (суб-пиксельно) — для всех глифов, КРОМЕ
 * `play_arrow`: для него — ОПТИЧЕСКИЙ центр = центр масс закрашенной фигуры, `Σ(покрытие·x) / Σ покрытие`
 * (треугольник с bbox по центру визуально «уезжает» влево, потому что масса лежит у плоского основания).
 * Глиф Material Symbols уже нарисован так, что центр масс — в 12.65/960 em левее центра em-квадрата
 * (0.7px при 56dp), поэтому слот-центрированный `play_arrow` и есть оптически центрированный.
 *
 * **Допуски** (проверяются оба):
 *  1. плотность 1x: `|центр иконки − центр круга| ≤ 1px` по обеим осям — то самое требование «≤ 1px»;
 *  2. любая плотность: `|центр иконки − центр круга − дизайнерское смещение глифа| ≤ 1px`. Дизайнерское
 *     смещение — где сам шрифт держит выбранную метрику относительно em-квадрата ([GLYPH_DESIGN_OFFSETS],
 *     по контурам TTF): `arrow_back` — bbox на +8.5/960 em вправо, `replay_10`/`forward_10` — на 11.26/960 em
 *     ВЫШЕ (стрелка сверху), `play_arrow` — центр масс на −12.65/960 em. На 2x/3x эти доли em дают больше 1
 *     физического пикселя (у 40dp на 3x — 1.4px), но это замысел дизайнеров шрифта, а не ошибка позиционирования.
 *
 * Отчёты (числа) и PNG-кропы с разметкой складываются в `composeApp/build/reports/playerIconCentering`
 * (каталог сборки, в git не попадает).
 */
@OptIn(ExperimentalTestApi::class)
class PlayerIconCenteringTest {
    private val controller = EmbedVideoController()

    @Test
    fun anixIconGlyphIsCenteredInItsBox() {
        val cases = mutableListOf<CenteringCase>()
        for (density in DENSITIES) {
            for (spec in ICON_SPECS) cases += renderAnixIcon(spec, density, inheritedTextStyle = false)
        }
        finish("anixIcon", cases)
    }

    /** Иконка внутри `Button`/чипа/пункта навигации наследует `lineHeight`/`letterSpacing` — центровку это не меняет. */
    @Test
    fun anixIconCenteringIgnoresInheritedTextStyle() {
        val cases = mutableListOf<CenteringCase>()
        for (density in DENSITIES) {
            for (spec in ICON_SPECS.take(INHERITED_STYLE_SPECS)) {
                cases += renderAnixIcon(spec, density, inheritedTextStyle = true)
            }
        }
        finish("anixIconInheritedStyle", cases)
    }

    @Test
    fun compactPlayerChromeButtonsAreCentered() {
        val cases = mutableListOf<CenteringCase>()
        for (density in DENSITIES) {
            for (isPlaying in listOf(false, true)) cases += renderCompactChrome(isPlaying, density)
        }
        finish("compactChrome", cases)
    }

    @Test
    fun seekFlashIndicatorIsCentered() {
        val cases = mutableListOf<CenteringCase>()
        for (density in DENSITIES) {
            for (direction in PlayerSeekDirection.entries) cases += renderSeekFlash(direction, density)
        }
        finish("seekFlash", cases)
    }

    @Test
    fun fullscreenOverlayButtonsAreCentered() {
        val cases = mutableListOf<CenteringCase>()
        for (density in DENSITIES) {
            for (isPlaying in listOf(false, true)) cases += renderFullscreenOverlay(isPlaying, density)
        }
        finish("fullscreenOverlay", cases)
    }

    @Test
    fun audioPickerCloseButtonIsCentered() {
        val cases = mutableListOf<CenteringCase>()
        for (density in DENSITIES) cases += renderAudioPickerClose(density)
        finish("audioPickerClose", cases)
    }

    // -- сцены ------------------------------------------------------------------------------

    private fun renderAnixIcon(
        spec: IconSpec,
        density: Float,
        inheritedTextStyle: Boolean,
    ): CenteringCase {
        val boxDp = spec.sizeDp + ICON_SCENE_MARGIN_DP
        val boxPx = (boxDp * density).toInt()
        var result: CenteringCase? = null
        runSkikoComposeUiTest(size = Size(boxPx.toFloat(), boxPx.toFloat()), density = Density(density)) {
            setContent {
                GrayScene {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val icon = @Composable {
                            AnixIcon(
                                name = spec.name,
                                contentDescription = null,
                                filled = spec.filled,
                                tint = Color.White,
                                modifier = Modifier.size(spec.sizeDp.dp),
                            )
                        }
                        if (inheritedTextStyle) {
                            // Как у иконки внутри Button/чипа: чужие lineHeight и letterSpacing.
                            ProvideTextStyle(TextStyle(lineHeight = HOSTILE_LINE_HEIGHT_SP.sp, letterSpacing = HOSTILE_SPACING_SP.sp), icon)
                        } else {
                            icon()
                        }
                    }
                }
            }
            val whole = PxRect(0, 0, boxPx, boxPx)
            val frame = awaitGlyph(whole)
            val icon = measureIcon(frame, whole, sceneBaseLuma(frame))
            val reference = Pt(whole.centerX, whole.centerY)
            val tag = "${spec.tag}${if (inheritedTextStyle) "_style" else ""}"
            saveAnnotatedCrop(frame, whole, null, reference, icon, cropScale(whole), reportFile("anixIcon_${tag}_d$density"))
            result = CenteringCase("anixIcon $tag", density, spec.name, spec.sizeDp, reference, icon)
        }
        return checkNotNull(result)
    }

    private fun renderCompactChrome(
        isPlaying: Boolean,
        density: Float,
    ): List<CenteringCase> {
        val out = mutableListOf<CenteringCase>()
        runSkikoComposeUiTest(size = Size(COMPACT_W * density, COMPACT_H * density), density = Density(density)) {
            setContent {
                GrayScene {
                    CompactPlayerChrome(
                        videoHeight = COMPACT_VIDEO_H.dp,
                        topOffset = 0.dp,
                        onBelowContentHeightMeasured = {},
                        state = EmbedVideoState(isVideoFound = true, isPlaying = isPlaying),
                        controller = controller,
                        onBack = {},
                        onEnterFullscreen = {},
                        voiceTypes = emptyList(),
                        currentVoiceType = null,
                        onOpenAudioPicker = {},
                    )
                }
            }
            waitForIdle()
            val playPause =
                if (isPlaying) {
                    ButtonSpec(
                        "Pause",
                        "pause",
                        28,
                        circle = true,
                    )
                } else {
                    ButtonSpec("Play", "play_arrow", 28, circle = true)
                }
            val buttons =
                if (isPlaying) {
                    listOf(playPause)
                } else {
                    listOf(
                        ButtonSpec("Back", "arrow_back", 24, circle = true),
                        ButtonSpec("Fullscreen", "fullscreen", 24, circle = true),
                        playPause,
                    )
                }
            for (button in buttons) out += measureNode("compactChrome", button, density)
        }
        return out
    }

    private fun renderSeekFlash(
        direction: PlayerSeekDirection,
        density: Float,
    ): CenteringCase {
        var result: CenteringCase? = null
        val state = PlayerSeekFlashState().also { it.fire(direction) }
        runSkikoComposeUiTest(size = Size(COMPACT_W * density, SEEK_H * density), density = Density(density)) {
            mainClock.autoAdvance = false
            setContent { GrayScene { PlayerSeekFlashOverlay(state) } }
            mainClock.advanceTimeBy(SEEK_SETTLE_MS)
            val roi =
                if (direction == PlayerSeekDirection.BACK) {
                    PxRect(0, 0, (SEEK_ROI_W * density).toInt(), (SEEK_H * density).toInt())
                } else {
                    PxRect(((COMPACT_W - SEEK_ROI_W) * density).toInt(), 0, (COMPACT_W * density).toInt(), (SEEK_H * density).toInt())
                }
            val frame = awaitGlyph(roi, manualClock = true)
            val base = sceneBaseLuma(frame)
            val plateau = darkestLuma(frame, roi)
            val circle = measureCircle(frame, roi, base, plateau)
            val icon = measureIcon(frame, roi, plateau, circle)
            val iconName = if (direction == PlayerSeekDirection.BACK) "replay_10" else "forward_10"
            saveAnnotatedCrop(frame, roi, circle, circle.center, icon, cropScale(roi), reportFile("seekFlash_${direction.name}_d$density"))
            result = CenteringCase("seekFlash ${direction.name}", density, iconName, SEEK_ICON_DP, circle.center, icon, circle)
        }
        return checkNotNull(result)
    }

    private fun renderFullscreenOverlay(
        isPlaying: Boolean,
        density: Float,
    ): List<CenteringCase> {
        val out = mutableListOf<CenteringCase>()
        runSkikoComposeUiTest(size = Size(FULL_W * density, FULL_H * density), density = Density(density)) {
            setContent {
                GrayScene {
                    PlayerOverlay(
                        state = EmbedVideoState(isVideoFound = true, isPlaying = isPlaying),
                        controller = controller,
                        hasNextEpisode = false,
                        onBack = {},
                        onCollapseFullscreen = {},
                        onNextEpisode = {},
                        onEpisodeNearEnd = {},
                        onEnterPictureInPicture = {},
                    )
                }
            }
            waitForIdle()
            val playPause =
                if (isPlaying) ButtonSpec("Pause", "pause", FULL_PLAY_ICON_DP) else ButtonSpec("Play", "play_arrow", FULL_PLAY_ICON_DP)
            val buttons =
                if (isPlaying) {
                    listOf(playPause)
                } else {
                    listOf(
                        ButtonSpec("Back", "arrow_back", FULL_ICON_DP),
                        ButtonSpec("Picture in picture", "picture_in_picture_alt", FULL_ICON_DP),
                        ButtonSpec("Rewind 10 seconds", "replay_10", FULL_ICON_DP),
                        playPause,
                        ButtonSpec("Forward 10 seconds", "forward_10", FULL_ICON_DP),
                    )
                }
            for (button in buttons) out += measureNode("fullscreen", button, density)
        }
        return out
    }

    private fun renderAudioPickerClose(density: Float): CenteringCase {
        var result: CenteringCase? = null
        startKoin { modules(module { single { LocalVoicePinStore(MapSettings()) } }) }
        try {
            runSkikoComposeUiTest(size = Size(FULL_W * density, FULL_H * density), density = Density(density)) {
                setContent {
                    GrayScene {
                        AudioPickerOverlay(
                            voiceTypes = listOf(VoiceType(id = 1, name = "AniDUB"), VoiceType(id = 2, name = "SHIZA Project")),
                            currentVoiceType = null,
                            isSwitching = false,
                            onSelect = {},
                            onDismiss = {},
                        )
                    }
                }
                waitForIdle()
                result =
                    measureNode(
                        "audioPickerClose",
                        ButtonSpec("Close", "close", PICKER_CLOSE_ICON_DP, circle = true, lighterCircle = true),
                        density,
                    )
            }
        } finally {
            stopKoin()
        }
        return checkNotNull(result)
    }

    // -- измерения --------------------------------------------------------------------------

    /** Находит кнопку по contentDescription, проверяет тач-таргет и измеряет иконку внутри её границ. */
    private fun SkikoComposeUiTest.measureNode(
        scene: String,
        button: ButtonSpec,
        density: Float,
    ): CenteringCase {
        val bounds = onNodeWithContentDescription(button.description).fetchSemanticsNode().boundsInRoot
        assertTrue(
            bounds.width >= MIN_TOUCH_TARGET_DP * density - TOUCH_EPS_PX && bounds.height >= MIN_TOUCH_TARGET_DP * density - TOUCH_EPS_PX,
            "${button.description}: touch target ${bounds.width}x${bounds.height}px is below ${MIN_TOUCH_TARGET_DP}dp at density $density",
        )
        val roi = PxRect(floor(bounds.left).toInt(), floor(bounds.top).toInt(), ceil(bounds.right).toInt(), ceil(bounds.bottom).toInt())
        val nodeCenter = Pt((bounds.left + bounds.right) / 2.0, (bounds.top + bounds.bottom) / 2.0)
        val frame = awaitGlyph(roi)
        val base = frame.luma(roi.left, roi.top)
        val plateau =
            when {
                !button.circle -> base
                button.lighterCircle -> lumaAt(frame, nodeCenter.x + LIGHT_CIRCLE_PROBE_DP * density, nodeCenter.y)
                else -> darkestLuma(frame, roi)
            }
        val circle = if (button.circle) measureCircle(frame, roi, base, plateau) else null
        val icon = measureIcon(frame, roi, plateau, circle)
        val reference = circle?.center ?: nodeCenter
        val tag = button.description.replace(' ', '_')
        saveAnnotatedCrop(frame, roi, circle, reference, icon, cropScale(roi), reportFile("${scene}_${tag}_d$density"))
        return CenteringCase("$scene ${button.description}", density, button.icon, button.iconDp, reference, icon, circle, nodeCenter)
    }

    /** Ждёт, пока в [roi] появятся пиксели иконки (шрифт Material Symbols грузится асинхронно). */
    private fun SkikoComposeUiTest.awaitGlyph(
        roi: PxRect,
        manualClock: Boolean = false,
    ): Frame {
        var frame = captureToImage().toFrame()
        var attempts = 0
        while (!hasBrightPixels(frame, roi, frame.luma(roi.left, roi.top)) && attempts < MAX_AWAIT_ATTEMPTS) {
            if (manualClock) mainClock.advanceTimeByFrame() else waitForIdle()
            Thread.sleep(AWAIT_SLEEP_MS)
            frame = captureToImage().toFrame()
            attempts++
        }
        return frame
    }

    @Composable
    private fun GrayScene(content: @Composable BoxScope.() -> Unit) {
        AppTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize().background(Color(SCENE_BACKGROUND_ARGB)), content = content)
        }
    }

    private fun finish(
        name: String,
        cases: List<CenteringCase>,
    ) {
        val text = cases.joinToString("\n") { it.line() }
        reportFile("report_$name", "txt").apply { parentFile?.mkdirs() }.writeText(text + "\n")
        println("=== $name ===\n$text")
        val bad = cases.filter { !it.withinTolerance() }
        assertTrue(
            bad.isEmpty(),
            "icon is off-center (limit ${TOLERANCE_PX}px) in ${bad.size}/${cases.size} cases:\n" + bad.joinToString("\n") { it.line() },
        )
    }

    private fun reportFile(
        name: String,
        extension: String = "png",
    ): File = File("build/reports/playerIconCentering", "$name.$extension")

    private fun cropScale(roi: PxRect): Int =
        (CROP_TARGET_PX / maxOf(roi.right - roi.left, roi.bottom - roi.top)).coerceIn(1, MAX_CROP_SCALE)

    /** Одна измеренная пара «иконка / опорный центр». */
    private class CenteringCase(
        val label: String,
        val density: Float,
        val iconName: String,
        val iconDp: Int,
        val reference: Pt,
        val icon: IconMeasurement,
        val circle: CircleMeasurement? = null,
        val nodeCenter: Pt? = null,
    ) {
        private val optical = iconName == PLAY_ARROW
        private val design = GLYPH_DESIGN_OFFSETS[iconName] ?: (0.0 to 0.0)
        private val emPx = iconDp * density

        /** Центр иконки по метрике: bbox (суб-пиксельно) либо центр масс для `play_arrow`. */
        val measured: Pt get() = if (optical) icon.centroid else icon.bboxCenter
        val dx: Double get() = measured.x - reference.x
        val dy: Double get() = measured.y - reference.y

        /** Дизайнерское смещение выбранной метрики от центра em-квадрата (px), по контурам глифа в шрифте. */
        val designDx: Double get() = design.first / FONT_UNITS_PER_EM * emPx
        val designDy: Double get() = design.second / FONT_UNITS_PER_EM * emPx

        fun withinTolerance(): Boolean {
            val rawOk = density != 1f || (abs(dx) <= TOLERANCE_PX && abs(dy) <= TOLERANCE_PX)
            val placementOk = abs(dx - designDx) <= TOLERANCE_PX && abs(dy - designDy) <= TOLERANCE_PX
            return rawOk && placementOk
        }

        fun line(): String =
            "%-36s d=%.0fx %-8s em=%.0fpx raw dx=%+.2f dy=%+.2f | design dx=%+.2f dy=%+.2f | placement dx=%+.2f dy=%+.2f | (%.2f dp) | ref=%s icon=%s bbox=%s%s %s"
                .format(
                    label,
                    density,
                    if (optical) "centroid" else "bbox",
                    emPx,
                    dx,
                    dy,
                    designDx,
                    designDy,
                    dx - designDx,
                    dy - designDy,
                    maxOf(abs(dx), abs(dy)) / density,
                    reference,
                    measured,
                    icon.bbox,
                    circle?.let { " circle=${it.center} r=${"%.2f".format(it.radius)}" } ?: "",
                    if (withinTolerance()) "OK" else "OFF-CENTER",
                )
    }

    private class IconSpec(
        val name: String,
        val sizeDp: Int,
        val filled: Boolean,
    ) {
        val tag: String get() = "${name}_${sizeDp}${if (filled) "f" else ""}"
    }

    private class ButtonSpec(
        val description: String,
        val icon: String,
        val iconDp: Int,
        val circle: Boolean = false,
        val lighterCircle: Boolean = false,
    )

    private companion object {
        const val TOLERANCE_PX = 1.0
        const val PLAY_ARROW = "play_arrow"
        const val FONT_UNITS_PER_EM = 960.0
        const val MIN_TOUCH_TARGET_DP = 48
        const val TOUCH_EPS_PX = 0.5f
        const val LIGHT_CIRCLE_PROBE_DP = 12
        const val HOSTILE_LINE_HEIGHT_SP = 64
        const val HOSTILE_SPACING_SP = 8
        const val INHERITED_STYLE_SPECS = 6
        const val ICON_SCENE_MARGIN_DP = 40
        const val COMPACT_W = 390
        const val COMPACT_H = 300
        const val COMPACT_VIDEO_H = 220
        const val SEEK_H = 220
        const val SEEK_ROI_W = 110
        const val SEEK_ICON_DP = 40
        const val SEEK_SETTLE_MS = 200L
        const val FULL_W = 800
        const val FULL_H = 450
        const val FULL_ICON_DP = 32
        const val FULL_PLAY_ICON_DP = 56
        const val PICKER_CLOSE_ICON_DP = 24
        const val MAX_AWAIT_ATTEMPTS = 100
        const val AWAIT_SLEEP_MS = 40L
        const val CROP_TARGET_PX = 360
        const val MAX_CROP_SCALE = 10

        val DENSITIES = listOf(1f, 2f, 3f)

        /**
         * Где шрифт `material_symbols_rounded.ttf` (FILL 0/1, wght 400, GRAD 0, opsz 24) держит выбранную
         * метрику центра относительно центра em-квадрата, в 1/960 em, оси экрана (+x вправо, +y вниз).
         * bbox — по границам контуров: `arrow_back` x 177..800 (центр 488.5), y 176.5..783 (центр 479.7);
         * `replay_10`/`forward_10` y 80..902.5 (центр 491.3 над центром квадрата 480 → −11.26). Для
         * `play_arrow` — центр масс контура (треугольник x 320..725; центр масс 467.4 → −12.65).
         * Остальные глифы (pause, close, fullscreen, picture_in_picture_alt) симметричны — 0.
         */
        val GLYPH_DESIGN_OFFSETS: Map<String, Pair<Double, Double>> =
            mapOf(
                "arrow_back" to (8.5 to 0.26),
                "replay_10" to (0.0 to -11.26),
                "forward_10" to (0.0 to -11.26),
                PLAY_ARROW to (-12.65 to 0.0),
            )

        val ICON_SPECS =
            listOf(
                IconSpec("arrow_back", 24, false),
                IconSpec("pause", 28, true),
                IconSpec(PLAY_ARROW, 28, true),
                IconSpec("replay_10", 40, true),
                IconSpec("fullscreen", 24, true),
                IconSpec("close", 24, true),
                IconSpec("arrow_back", 32, false),
                IconSpec("pause", 56, true),
                IconSpec(PLAY_ARROW, 56, true),
                IconSpec("replay_10", 32, true),
                IconSpec("forward_10", 40, true),
                IconSpec("picture_in_picture_alt", 32, false),
            )
    }
}
