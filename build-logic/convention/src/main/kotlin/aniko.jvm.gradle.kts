import com.aniko.buildlogic.libsCatalog
import com.aniko.buildlogic.version

/**
 * Плагин для чистых Kotlin/JVM-модулей корневой сборки, не входящих в KMP-граф. Единственный
 * потребитель (2026-08-07) — `:detekt-rules` (P2.T10): кастомное detekt-правило должно жить в
 * корневой сборке из-за границы includeBuild (подробности — KDoc в
 * `detekt-rules/build.gradle.kts`), но само по себе это обычный однотаргетный Kotlin/JVM модуль,
 * а не KMP-таргет.
 *
 * Через convention-плагин, а НЕ `alias(libs.plugins.kotlin.jvm)` напрямую в потребителе:
 * `org.jetbrains.kotlin.jvm` реализован тем же артефактом `kotlin-gradle-plugin`, что уже
 * присутствует в classpath build-logic (см. `implementation(libs.plugin.kotlin.multiplatform)` в
 * `build-logic/convention/build.gradle.kts`). Прямой `alias(...)` в модуле корневой сборки
 * заставляет Gradle резолвить и грузить ВТОРОЙ экземпляр Kotlin Gradle Plugin другим
 * class loader'ом — эмпирически проверено: именно так и произошло при первой попытке
 * (`detekt-rules/build.gradle.kts` с `alias(libs.plugins.kotlin.jvm)`), Gradle предупредил "The
 * Kotlin Gradle plugin was loaded multiple times in different subprojects... may break the
 * build" (':composeApp' и ':detekt-rules'). Через этот convention-плагин переиспользуется уже
 * загруженный build-logic'ом class loader — предупреждение пропадает.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(libsCatalog.version("jvmTarget").toInt())
}
