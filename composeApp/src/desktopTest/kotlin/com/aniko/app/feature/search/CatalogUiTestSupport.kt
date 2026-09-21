package com.aniko.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.aniko.app.smoke.noOpImageLoader
import com.aniko.model.Release
import com.aniko.model.ReleaseStatus
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AppTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Каталог скриншотов теста: системное свойство `aniko.screenshotDir` (если задано) или
 * `build/reports/catalogToolbar` рядом с модулем — тот же приём, что в `EpisodeGridColorSmokeTest`.
 */
internal val SCREENSHOT_DIR: File =
    File(System.getProperty("aniko.screenshotDir") ?: "build/reports/catalogToolbar")

/** Сбрасывает кадр в PNG `SCREENSHOT_DIR/<name>.png` для глазной сверки. */
internal fun saveShot(
    frame: ImageBitmap,
    name: String,
) {
    val pixels = frame.toPixelMap().buffer
    val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, frame.width, frame.height, pixels, 0, frame.width)
    val out = File(SCREENSHOT_DIR, "$name.png")
    out.parentFile?.mkdirs()
    ImageIO.write(image, "png", out)
}

/**
 * Рисует [content] в тестовом окне размера [size] (px = dp при `density = 1f`) поверх [AppTheme]
 * с заданными темой, локалью и классом размера окна — без Koin/ViewModel (см. KDoc
 * `CatalogScreenContent`).
 */
@OptIn(ExperimentalTestApi::class)
internal fun runCatalogUiTest(
    size: Size,
    windowSize: AnixWindowSize,
    strings: Strings,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
    body: SkikoComposeUiTest.() -> Unit,
) {
    SingletonImageLoader.setSafe { context -> noOpImageLoader(context) }
    runSkikoComposeUiTest(size = size, density = Density(1f)) {
        setContent {
            AppTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalStrings provides strings,
                    LocalAnixWindowSize provides windowSize,
                ) {
                    content()
                }
            }
        }
        body()
    }
}

/** Синтетическая выдача Каталога: разные статусы и рейтинги, названия разной длины. */
internal fun sampleReleases(count: Int = 12): List<Release> {
    val titles =
        listOf(
            "Магическая битва",
            "Клинок, рассекающий демонов: Квартал красных фонарей",
            "Твоё имя",
            "Провожающая в последний путь Фрирен",
            "Ванпанчмен",
            "Атака титанов",
            "Стальной алхимик: Братство",
            "Ковбой Бибоп",
        )
    val statuses = listOf(ReleaseStatus.ONGOING, ReleaseStatus.FINISHED, ReleaseStatus.ANNOUNCE)
    return List(count) { index ->
        Release(
            id = 1000 + index,
            title = titles[index % titles.size],
            description = "Описание тайтла номер ${index + 1}: краткий синопсис для списка на телефоне.",
            episodesReleased = 12 + index,
            grade = 3.5 + (index % 15) / 10.0,
            status = statuses[index % statuses.size],
        )
    }
}

/** Пиксель `0xAARRGGBB` кадра. */
internal fun ImageBitmap.pixel(
    x: Int,
    y: Int,
): Int = toPixelMap().buffer[y * width + x]

/** Максимальная по каналам разница двух цветов `0xAARRGGBB`. */
internal fun channelDistance(
    a: Int,
    b: Int,
): Int =
    maxOf(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

/**
 * Считает «чернильные» пиксели в прямоугольнике `[left, right) x [top, bottom)`: отличающиеся от
 * [background] больше чем на [threshold] по любому каналу. Так проверяется, что подсказка/текст
 * реально нарисованы (а не обрезаны в пустой прямоугольник).
 */
internal fun ImageBitmap.inkPixels(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    background: Int,
    threshold: Int = 40,
): Int {
    val buffer = toPixelMap().buffer
    var count = 0
    for (y in top until bottom) {
        for (x in left until right) {
            if (channelDistance(buffer[y * width + x], background) > threshold) count++
        }
    }
    return count
}

/**
 * Имитация каркаса `AdaptiveScaffold` на Expanded: сайдбар 232.dp + `VerticalDivider` + отступ
 * 16.dp слева от контента (см. `AdaptiveScaffold.kt`/`SidebarSlot.kt`). Без неё офскрин-кадр
 * получил бы всю ширину окна и число колонок сетки не совпало бы с реальным приложением.
 */
@Composable
internal fun ExpandedShell(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(232.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface))
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).padding(start = 16.dp)) { content() }
    }
}
