package com.aniko.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Кодогенерация `BuildInfo.kt` с версией приложения — единый источник версии живёт в
 * `composeApp/build.gradle.kts` (`anikoAppVersion`), оттуда же берётся `versionName`
 * Android-таргета. Зашивка константой в сгенерированный Kotlin-файл делает версию доступной на
 * всех трёх платформах (Android/iOS/Desktop) из `commonMain` без `expect/actual` и без
 * платформенных API чтения манифеста/bundle.
 *
 * Тот же паттерн, что и [GenerateApiFixturesTask]: значение кодируется обычным экранированным
 * строковым литералом (см. [toKotlinStringLiteral]), директория вывода подключается к source set
 * через `srcDir(task.flatMap { it.outputDir })` — провайдер-зависимость даёт корректную
 * упорядоченность задач без явного `dependsOn`.
 */
@CacheableTask
abstract class GenerateBuildInfoTask : DefaultTask() {
    /** Версия приложения (напр. `0.0.2`) — зашивается в `BuildInfo.APP_VERSION`. */
    @get:Input
    abstract val appVersion: Property<String>

    /** Куда сгенерировать `BuildInfo.kt` (директория — корень для `srcDir`, не сам файл). */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Package сгенерированного файла, напр. `com.aniko.app.buildinfo`. */
    @get:Input
    abstract val packageName: Property<String>

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val pkgDir = File(outputDir.get().asFile, pkg.replace('.', '/'))
        pkgDir.mkdirs()

        val content =
            buildString {
                appendLine("package $pkg")
                appendLine()
                appendLine("// АВТОГЕНЕРИРУЕМЫЙ ФАЙЛ — не редактировать вручную.")
                appendLine("// Источник версии: composeApp/build.gradle.kts (anikoAppVersion).")
                appendLine("// Генератор: GenerateBuildInfoTask (build-logic/convention), см. её KDoc.")
                appendLine()
                appendLine("/** Сведения о сборке, зашитые в код на этапе сборки. */")
                appendLine("object BuildInfo {")
                appendLine("    /** Версия приложения — единая для Android/iOS/Desktop. */")
                appendLine("    const val APP_VERSION: String = ${appVersion.get().toKotlinStringLiteral()}")
                appendLine("}")
            }

        File(pkgDir, "BuildInfo.kt").writeText(content)
    }
}
