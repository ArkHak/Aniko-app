package com.aniko.app.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.ui.component.ChipRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens
import com.aniko.ui.theme.AppTheme
import com.aniko.ui.theme.LocalAnixColors
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран-галерея дизайн-токенов (Фаза 2 плана, P2.T12): палитра/типографика/spacing/radius в
 * одном месте для визуальной проверки на всех платформах — плюс живые переключатели языка и
 * темы, чтобы заодно проверить runtime-переключение языка (P2.T8) без перезапуска.
 *
 * Debug-маршрут, открывается из [com.aniko.app.feature.settings.SettingsScreen] — вне табовой
 * навигации намеренно (см. `AnixDestination.TokenGallery`).
 *
 * Тема переключается локальным Compose-состоянием (визуальная проверка одного экрана,
 * персистентность не нужна) — оборачивает содержимое в собственный вложенный [AppTheme]. Язык
 * переключается по-настоящему через [TokenGalleryViewModel]/`LocaleStore`, поэтому виден во всём
 * приложении, а не только на этом экране.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenGalleryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TokenGalleryViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    // `remember { }` не умеет вызывать @Composable (DisallowComposableCalls) — читаем системную
    // тему снаружи, только начальное значение переключателя, дальше это обычный локальный стейт.
    val systemIsDark = isSystemInDarkTheme()
    var isDark by remember { mutableStateOf(systemIsDark) }
    // Индекс в FONT_SCALES — инструмент аудита P6.T12 (масштаб шрифта/переполнение RU-текста):
    // экран уже даёт RU/EN одновременно (TypographySection) и переключатель языка приложения,
    // не хватало только рычага для fontScale — теперь оба измерения (язык × масштаб) проверяются
    // в одном месте, без внешней screenshot-инфраструктуры (см. журнал Фазы 6 — в кодовой базе
    // нет ни одного `@Preview`, заводить такую инфраструктуру ради одного пункта дороже).
    var fontScaleIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(strings.galleryTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backContentDescription,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AppTheme(darkTheme = isDark) {
            val baseDensity = LocalDensity.current
            val scaledDensity =
                remember(baseDensity, fontScaleIndex) {
                    Density(density = baseDensity.density, fontScale = FONT_SCALES[fontScaleIndex])
                }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    val dimens = AnixThemeTokens.dimens
                    Column(
                        modifier =
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dimens.spaceM),
                        verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
                    ) {
                        GalleryControls(
                            fontScaleIndex = fontScaleIndex,
                            onFontScaleIndexChange = { fontScaleIndex = it },
                            languageTag = languageTag,
                            onLanguageTagChange = viewModel::setLanguageTag,
                            isDark = isDark,
                            onDarkChange = { isDark = it },
                        )
                        GallerySection(strings.galleryColorsSection) { ColorsSection() }
                        GallerySection(strings.galleryTypographySection) { TypographySection() }
                        GallerySection(strings.gallerySpacingSection) { SpacingSection() }
                        GallerySection(strings.galleryRadiusSection) { RadiusSection() }
                        GallerySection(strings.galleryComponentsSection) { ComponentsSection() }
                    }
                }
            }
        }
    }
}

// Демонстрационные шаги масштаба шрифта для аудита P6.T12 (100/130/200%) — не продуктовые
// константы, поэтому не вынесены в Dimens.
@Suppress("MagicNumber")
private val FONT_SCALES = listOf(1.0f, 1.3f, 2.0f)

@Suppress("LongParameterList") // Debug-экран галереи, все параметры — независимые переключатели
// одного и того же контрольного блока (P6.T12 добавил fontScale к уже бывшим языку/теме).
@Composable
private fun GalleryControls(
    fontScaleIndex: Int,
    onFontScaleIndexChange: (Int) -> Unit,
    languageTag: String?,
    onLanguageTagChange: (String?) -> Unit,
    isDark: Boolean,
    onDarkChange: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(text = strings.galleryFontScaleLabel, style = MaterialTheme.typography.labelLarge)
        ChipRow(
            items = FONT_SCALES.indices.toList(),
            isSelected = { it == fontScaleIndex },
            label = { "${FONT_SCALES[it]}x" },
            onClick = onFontScaleIndexChange,
        )

        Text(text = strings.galleryLanguageLabel, style = MaterialTheme.typography.labelLarge)
        ChipRow(
            items = listOf(null, "en", "ru"),
            isSelected = { it == languageTag },
            label = {
                when (it) {
                    "en" -> "EN"
                    "ru" -> "RU"
                    else -> strings.galleryLanguageSystem
                }
            },
            onClick = onLanguageTagChange,
        )

        Text(text = strings.galleryThemeLabel, style = MaterialTheme.typography.labelLarge)
        ChipRow(
            items = listOf(false, true),
            isSelected = { it == isDark },
            label = { if (it) strings.galleryThemeDark else strings.galleryThemeLight },
            onClick = onDarkChange,
        )
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ColorsSection() {
    val dimens = AnixThemeTokens.dimens
    val colorScheme = MaterialTheme.colorScheme
    val anixColors = LocalAnixColors.current
    val swatches =
        listOf(
            "primary" to colorScheme.primary,
            "onPrimary" to colorScheme.onPrimary,
            "secondary" to colorScheme.secondary,
            "onSecondary" to colorScheme.onSecondary,
            "tertiary" to colorScheme.tertiary,
            "error" to colorScheme.error,
            "onError" to colorScheme.onError,
            "background" to colorScheme.background,
            "onBackground" to colorScheme.onBackground,
            "surface" to colorScheme.surface,
            "onSurface" to colorScheme.onSurface,
            "surfaceVariant" to colorScheme.surfaceVariant,
            "outline" to colorScheme.outline,
            "success" to anixColors.success,
            "live" to anixColors.live,
        )

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        swatches.chunked(SWATCHES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                row.forEach { (name, color) -> ColorSwatch(name = name, color = color) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    name: String,
    color: Color,
) {
    val dimens = AnixThemeTokens.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        val swatchShape = RoundedCornerShape(dimens.cornerS)
        Box(
            modifier =
                Modifier
                    .size(SWATCH_SIZE)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), swatchShape)
                    .background(color, swatchShape),
        )
        Text(text = name, style = MaterialTheme.typography.labelSmall)
    }
}

/** Пример на RU и EN одновременно (не переключается языком приложения — оба нужны разом для сверки). */
@Composable
private fun TypographySection() {
    val dimens = AnixThemeTokens.dimens
    val typography = MaterialTheme.typography
    val styles =
        listOf(
            "displayLarge" to typography.displayLarge,
            "displayMedium" to typography.displayMedium,
            "displaySmall" to typography.displaySmall,
            "headlineLarge" to typography.headlineLarge,
            "headlineMedium" to typography.headlineMedium,
            "headlineSmall" to typography.headlineSmall,
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall,
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall,
        )

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        styles.forEach { (name, style) -> TypographyRow(name, style) }
    }
}

// P2.T10: это НЕ хардкод UI-текста в обычном смысле — TokenGalleryScreen (P2.T12) намеренно
// показывает RU- и EN-образец одновременно, независимо от текущего языка приложения, специально
// для визуальной сверки типографики сразу на обоих языках (см. KDoc P2.T12 в
// docs/REELWAVE_PLAN.md: "не переключается языком приложения — оба нужны разом для сверки").
// Через Strings/Lyricist это не сделать (там ровно один активный язык), поэтому — осознанное
// исключение, не грандфазеренное через baseline.
@Suppress("ForbiddenCyrillicStringLiteral")
@Composable
private fun TypographyRow(
    name: String,
    style: TextStyle,
) {
    val dimens = AnixThemeTokens.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(text = name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = "Пример текста $name", style = style)
        Text(text = "Sample text $name", style = style)
    }
}

@Composable
private fun SpacingSection() {
    val dimens = AnixThemeTokens.dimens
    val spacings =
        listOf(
            "spaceXs (4dp)" to dimens.spaceXs,
            "spaceS (8dp)" to dimens.spaceS,
            "space12 (12dp)" to dimens.space12,
            "spaceM (16dp)" to dimens.spaceM,
            "spaceL (24dp)" to dimens.spaceL,
            "spaceXl (32dp)" to dimens.spaceXl,
            "space48 (48dp)" to dimens.space48,
        )

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        spacings.forEach { (name, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            ) {
                Box(
                    modifier =
                        Modifier
                            .height(SPACING_BAR_HEIGHT)
                            .width(value)
                            .background(MaterialTheme.colorScheme.primary),
                )
                Text(text = name, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RadiusSection() {
    val dimens = AnixThemeTokens.dimens
    val radii =
        listOf(
            "cornerS (8dp)" to dimens.cornerS,
            "cornerM (12dp)" to dimens.cornerM,
            "corner16 (16dp)" to dimens.corner16,
            "cornerL (20dp)" to dimens.cornerL,
            "cornerPill (999dp)" to dimens.cornerPill,
        )

    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        radii.forEach { (name, radius) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(RADIUS_BOX_SIZE)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(radius)),
                )
                Text(text = name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private const val SWATCHES_PER_ROW = 3
private val SWATCH_SIZE = 56.dp
private val SPACING_BAR_HEIGHT = 12.dp
private val RADIUS_BOX_SIZE = 48.dp
