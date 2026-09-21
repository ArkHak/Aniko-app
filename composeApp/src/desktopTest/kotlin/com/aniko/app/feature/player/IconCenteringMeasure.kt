package com.aniko.app.feature.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Офскрин-измерения центровки иконки внутри круга/кнопки (composeApp desktopTest).
 *
 * Метод: сцена рисуется на сплошном сером фоне ([SCENE_BACKGROUND_ARGB]); подложка-круг кнопок —
 * однотонная (полупрозрачный чёрный скрим темнее фона, либо светлая накладка 8% светлее фона), сама
 * иконка — самая светлая деталь кадра. Поэтому:
 *  - круг находится по «покрытию» пикселя между фоном и плато круга (порог 0.5), а его центр
 *    подгоняется по 96 лучам с суб-пиксельной интерполяцией края (устойчиво к тому, что внутри
 *    круга лежит иконка);
 *  - иконка — по «осветлению» относительно плато круга (или фона, если круга нет), внутри
 *    вписанного диска круга (кольцо антиалиасинга самого круга иконкой не считается);
 *  - у иконки считаются bbox видимых пикселей (порог покрытия 0.5, суб-пиксельные границы) и центр
 *    масс (сумма покрытия по пикселям): bbox-центр — метрика для всех глифов, кроме `play_arrow`,
 *    у которого метрика — центр масс (оптический центр треугольника).
 */

/** Сплошной серый фон сцен измерения (luma = 128): скрим темнее него, иконка светлее. */
internal const val SCENE_BACKGROUND_ARGB: Long = 0xFF808080

/** Кадр офскрин-рендера: ARGB-пиксели в порядке строк, как у `PixelMap.buffer`. */
internal class Frame(
    val width: Int,
    val height: Int,
    private val argb: IntArray,
) {
    /** Яркость пикселя (0..255); за границами кадра — 255 (не «тёмный», чтобы не шуметь в круге). */
    fun luma(
        x: Int,
        y: Int,
    ): Double {
        if (x < 0 || y < 0 || x >= width || y >= height) return MAX_LUMA
        val p = argb[y * width + x]
        val r = (p shr RED_SHIFT) and CHANNEL_MASK
        val g = (p shr GREEN_SHIFT) and CHANNEL_MASK
        val b = p and CHANNEL_MASK
        return LUMA_R * r + LUMA_G * g + LUMA_B * b
    }

    /** Билинейная выборка [f]`(luma)` в непрерывных координатах (центр пикселя i = i + 0.5). */
    fun sample(
        fx: Double,
        fy: Double,
        f: (Double) -> Double,
    ): Double {
        val x = fx - HALF
        val y = fy - HALF
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val tx = x - x0
        val ty = y - y0
        val top = f(luma(x0, y0)) * (1 - tx) + f(luma(x0 + 1, y0)) * tx
        val bottom = f(luma(x0, y0 + 1)) * (1 - tx) + f(luma(x0 + 1, y0 + 1)) * tx
        return top * (1 - ty) + bottom * ty
    }

    fun argbAt(
        x: Int,
        y: Int,
    ): Int = argb[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]

    private companion object {
        const val MAX_LUMA = 255.0
        const val LUMA_R = 0.299
        const val LUMA_G = 0.587
        const val LUMA_B = 0.114
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val CHANNEL_MASK = 0xFF
        const val HALF = 0.5
    }
}

internal fun ImageBitmap.toFrame(): Frame = Frame(width, height, toPixelMap().buffer)

/** Прямоугольник в пикселях кадра; [right]/[bottom] — исключающие границы. */
internal data class PxRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0

    fun inflate(px: Int): PxRect = PxRect(left - px, top - px, right + px, bottom + px)

    fun clampTo(frame: Frame): PxRect = PxRect(max(left, 0), max(top, 0), min(right, frame.width), min(bottom, frame.height))
}

internal data class Pt(
    val x: Double,
    val y: Double,
) {
    override fun toString(): String = "(%.2f, %.2f)".format(x, y)
}

internal class CircleMeasurement(
    val center: Pt,
    val radius: Double,
)

internal class IconMeasurement(
    /** bbox видимых пикселей иконки (покрытие >= 0.5), правая/нижняя границы исключающие. */
    val bbox: PxRect,
    /** Центр bbox с суб-пиксельными границами (интерполяция уровня покрытия 0.5). */
    val bboxCenter: Pt,
    /** Центр масс иконки (взвешен покрытием) — «оптический центр» для play_arrow. */
    val centroid: Pt,
    val pixels: Int,
)

private const val EDGE_COVERAGE = 0.5
private const val CIRCLE_RAYS = 96
private const val CIRCLE_ITERATIONS = 3
private const val RAY_STEP = 0.05
private const val RAY_OUTSIDE = 5.0
private const val RAY_INSIDE = 8.0
private const val MIN_CIRCLE_CONTRAST = 8.0
private const val NOISE_COVERAGE = 0.02
private const val ICON_INNER_MARGIN_PX = 2.0
private const val MAX_LUMA_VALUE = 255.0
private const val HALF_PIXEL = 0.5

/** Яркость фона сцены (luma серого [SCENE_BACKGROUND_ARGB]). */
internal fun sceneBaseLuma(frame: Frame): Double = frame.luma(0, 0)

/** Яркость пикселя кадра в точке [x], [y] (для замера плато круга рядом с иконкой). */
internal fun lumaAt(
    frame: Frame,
    x: Double,
    y: Double,
): Double = frame.luma(floor(x).toInt(), floor(y).toInt())

/** Самая тёмная яркость внутри [roi] — плато круга-скрима (иконки светлее, фон светлее скрима). */
internal fun darkestLuma(
    frame: Frame,
    roi: PxRect,
): Double {
    var darkest = Double.MAX_VALUE
    val r = roi.clampTo(frame)
    for (y in r.top until r.bottom) for (x in r.left until r.right) darkest = min(darkest, frame.luma(x, y))
    return darkest
}

/** Радиус (от центра [cx], [cy]) точки, где покрытие [dark] на луче под углом [a] пересекает 0.5, идя снаружи внутрь. */
private fun rayEdgeRadius(
    frame: Frame,
    cx: Double,
    cy: Double,
    a: Double,
    radius: Double,
    dark: (Double) -> Double,
): Double? {
    var rr = radius + RAY_OUTSIDE
    var prev = frame.sample(cx + rr * cos(a), cy + rr * sin(a), dark)
    while (rr > radius - RAY_INSIDE) {
        val next = rr - RAY_STEP
        val d = frame.sample(cx + next * cos(a), cy + next * sin(a), dark)
        if (d >= EDGE_COVERAGE) return rr + (next - rr) * ((EDGE_COVERAGE - prev) / (d - prev))
        prev = d
        rr = next
    }
    return null
}

/**
 * Находит круг в [roi]: [base] — яркость фона вокруг, [plateau] — яркость однотонной заливки круга
 * (темнее или светлее фона). Центр — среднее по 96 точкам края (покрытие 0.5, суб-пиксельно),
 * 3 итерации уточнения.
 */
internal fun measureCircle(
    frame: Frame,
    roi: PxRect,
    base: Double,
    plateau: Double,
): CircleMeasurement {
    val span = plateau - base
    require(abs(span) > MIN_CIRCLE_CONTRAST) { "no visible circle in $roi: base=$base plateau=$plateau" }

    fun cover(l: Double): Double = ((l - base) / span).coerceIn(0.0, 1.0)
    val r0 = roi.clampTo(frame)
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    for (y in r0.top until r0.bottom) {
        for (x in r0.left until r0.right) {
            if (cover(frame.luma(x, y)) >= EDGE_COVERAGE) {
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
            }
        }
    }
    var cx = (minX + maxX + 1) / 2.0
    var cy = (minY + maxY + 1) / 2.0
    var radius = ((maxX + 1 - minX) + (maxY + 1 - minY)) / 4.0
    repeat(CIRCLE_ITERATIONS) {
        val edges =
            (0 until CIRCLE_RAYS).mapNotNull { k ->
                val a = 2 * PI * k / CIRCLE_RAYS
                rayEdgeRadius(frame, cx, cy, a, radius, ::cover)?.let { Pt(cx + it * cos(a), cy + it * sin(a)) }
            }
        require(edges.size > CIRCLE_RAYS / 2) { "circle edge not found in $roi" }
        val ncx = edges.sumOf { it.x } / edges.size
        val ncy = edges.sumOf { it.y } / edges.size
        radius = edges.sumOf { hypot(it.x - ncx, it.y - ncy) } / edges.size
        cx = ncx
        cy = ncy
    }
    return CircleMeasurement(Pt(cx, cy), radius)
}

/**
 * Измеряет иконку: белые пиксели светлее [plateau] (яркость подложки под иконкой — фона или
 * плато круга). С [circle] учитываются только пиксели внутри вписанного диска (радиус − 2px).
 */
internal fun measureIcon(
    frame: Frame,
    roi: PxRect,
    plateau: Double,
    circle: CircleMeasurement? = null,
): IconMeasurement {
    val span = MAX_LUMA_VALUE - plateau
    val r = roi.clampTo(frame)
    val colMax = DoubleArray(frame.width)
    val rowMax = DoubleArray(frame.height)
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    var sumW = 0.0
    var sumX = 0.0
    var sumY = 0.0
    var pixels = 0
    for (y in r.top until r.bottom) {
        for (x in r.left until r.right) {
            if (circle != null &&
                hypot(x + 0.5 - circle.center.x, y + 0.5 - circle.center.y) > circle.radius - ICON_INNER_MARGIN_PX
            ) {
                continue
            }
            val cov = ((frame.luma(x, y) - plateau) / span).coerceIn(0.0, 1.0)
            if (cov >= NOISE_COVERAGE) {
                sumW += cov
                sumX += cov * (x + 0.5)
                sumY += cov * (y + 0.5)
                colMax[x] = max(colMax[x], cov)
                rowMax[y] = max(rowMax[y], cov)
            }
            if (cov >= EDGE_COVERAGE) {
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                pixels++
            }
        }
    }
    require(pixels > 0 && sumW > 0.0) { "no icon pixels in $roi (plateau=$plateau)" }
    val bbox = PxRect(minX, minY, maxX + 1, maxY + 1)
    val (left, right) = subPixelExtent(colMax, minX, maxX)
    val (top, bottom) = subPixelExtent(rowMax, minY, maxY)
    return IconMeasurement(
        bbox = bbox,
        bboxCenter = Pt((left + right) / 2, (top + bottom) / 2),
        centroid = Pt(sumX / sumW, sumY / sumW),
        pixels = pixels,
    )
}

/**
 * Суб-пиксельные границы фигуры по профилю покрытия [profile] (максимум покрытия по
 * перпендикулярной оси в каждой строке/столбце): линейная интерполяция пересечения уровня 0.5
 * между центрами соседних пикселей. [first]/[last] — первый/последний индекс с покрытием >= 0.5.
 */
private fun subPixelExtent(
    profile: DoubleArray,
    first: Int,
    last: Int,
): Pair<Double, Double> {
    val start =
        if (first == 0) {
            0.0
        } else {
            val prev = profile[first - 1]
            val cur = profile[first]
            (first - HALF_PIXEL) + (EDGE_COVERAGE - prev) / (cur - prev)
        }
    val end =
        if (last >= profile.size - 1) {
            profile.size.toDouble()
        } else {
            val cur = profile[last]
            val next = profile[last + 1]
            (last + HALF_PIXEL) + (cur - EDGE_COVERAGE) / (cur - next)
        }
    return start to end
}

/** Есть ли в [roi] хоть один заметно более светлый, чем [base], пиксель (иконка уже нарисована). */
internal fun hasBrightPixels(
    frame: Frame,
    roi: PxRect,
    base: Double,
    minPixels: Int = 8,
): Boolean {
    val r = roi.clampTo(frame)
    var n = 0
    for (y in r.top until r.bottom) {
        for (x in r.left until r.right) if (frame.luma(x, y) > base + 60.0) n++
    }
    return n >= minPixels
}

/** Сохраняет PNG кадра целиком. */
internal fun saveFramePng(
    frame: Frame,
    file: File,
) {
    val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until frame.height) for (x in 0 until frame.width) image.setRGB(x, y, frame.argbAt(x, y))
    file.parentFile?.mkdirs()
    ImageIO.write(image, "png", file)
}

/**
 * PNG-кроп [roi] с увеличением [scale]× (ближайший сосед) и разметкой: зелёная окружность —
 * найденный круг, жёлтый крест — опорный центр, красная рамка — bbox иконки, голубая точка —
 * центр bbox, пурпурная точка — центр масс.
 */
internal fun saveAnnotatedCrop(
    frame: Frame,
    roi: PxRect,
    circle: CircleMeasurement?,
    reference: Pt,
    icon: IconMeasurement,
    scale: Int,
    file: File,
) {
    val r = roi.clampTo(frame)
    val w = (r.right - r.left) * scale
    val h = (r.bottom - r.top) * scale
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until h) for (x in 0 until w) image.setRGB(x, y, frame.argbAt(r.left + x / scale, r.top + y / scale))
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    fun sx(v: Double) = ((v - r.left) * scale).toInt()

    fun sy(v: Double) = ((v - r.top) * scale).toInt()
    g.stroke = BasicStroke(1f)
    if (circle != null) {
        g.color = Color(0x00, 0xE0, 0x00)
        val rad = (circle.radius * scale).toInt()
        g.drawOval(sx(circle.center.x) - rad, sy(circle.center.y) - rad, rad * 2, rad * 2)
    }
    g.color = Color(0xFF, 0xE0, 0x00)
    g.drawLine(sx(reference.x) - 10, sy(reference.y), sx(reference.x) + 10, sy(reference.y))
    g.drawLine(sx(reference.x), sy(reference.y) - 10, sx(reference.x), sy(reference.y) + 10)
    g.color = Color(0xFF, 0x30, 0x30)
    g.drawRect(
        sx(icon.bbox.left.toDouble()),
        sy(icon.bbox.top.toDouble()),
        (icon.bbox.right - icon.bbox.left) * scale,
        (icon.bbox.bottom - icon.bbox.top) * scale,
    )
    g.color = Color(0x00, 0xE0, 0xFF)
    g.fillOval(sx(icon.bboxCenter.x) - 3, sy(icon.bboxCenter.y) - 3, 6, 6)
    g.color = Color(0xFF, 0x00, 0xFF)
    g.fillOval(sx(icon.centroid.x) - 3, sy(icon.centroid.y) - 3, 6, 6)
    g.dispose()
    file.parentFile?.mkdirs()
    ImageIO.write(image, "png", file)
}
