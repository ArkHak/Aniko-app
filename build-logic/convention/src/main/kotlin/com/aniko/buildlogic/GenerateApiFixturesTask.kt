package com.aniko.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Кодогенерация JSON-фикстур API (F1, Фаза 11 — `docs/REELWAVE_PLAN.md`).
 *
 * Файловый I/O в `commonTest`/`desktopTest` на Kotlin/Native (и вообще при запуске тестов из
 * Gradle) не гарантирует рабочую директорию процесса — путь `docs/api/samples/...` относительно
 * неё попросту не всегда резолвится. Вместо чтения файлов в рантайме теста эта задача на этапе
 * СБОРКИ читает JSON-файлы из `docs/api/samples` и зашивает содержимое каждого файла как строковую
 * константу в сгенерированный Kotlin-объект — тесту остаётся только импортировать объект,
 * никакого I/O.
 *
 * Каждый JSON-файл превращается в одну константу объекта [packageName].`ApiFixtures`: имя
 * константы — camelCase от имени файла без расширения (`discover_interesting.json` →
 * `discoverInteresting`). Содержимое кодируется как ОБЫЧНЫЙ (не raw/triple-quoted) экранированный
 * строковый литерал — сознательный выбор вместо `"""..."""` из черновика задачи: raw-строка ломается,
 * если содержимое файла когда-нибудь будет содержать подряд идущие `"""`, а экранированный литерал
 * корректен для любого содержимого без дополнительных проверок.
 *
 * Регистрируется отдельно в каждом модуле, которому нужны фикстуры (`shared/data`, `composeApp`)
 * — с разным [packageName] на каждый вызов, чтобы не заводить общий тестовый артефакт между
 * модулями (у Kotlin Multiplatform нет чистого способа шарить `commonTest`/`desktopTest`-исходники
 * между модулями без `testFixtures`-инфраструктуры, которая для KMP не отработана в проекте).
 */
@CacheableTask
abstract class GenerateApiFixturesTask : DefaultTask() {
    /** Директория с исходными `*.json` (обычно `docs/api/samples` в корне репозитория). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val samplesDir: DirectoryProperty

    /** Куда сгенерировать `ApiFixtures.kt` (директория — корень для `srcDir`, не сам файл). */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Package сгенерированного файла, напр. `com.aniko.data.fixtures`. */
    @get:Input
    abstract val packageName: Property<String>

    @TaskAction
    fun generate() {
        val samples = samplesDir.get().asFile
        val pkg = packageName.get()
        val pkgDir = File(outputDir.get().asFile, pkg.replace('.', '/'))
        pkgDir.mkdirs()

        val files =
            samples
                .listFiles { file -> file.isFile && file.extension == "json" }
                ?.sortedBy { it.name }
                .orEmpty()

        val properties =
            files.joinToString("\n\n") { file ->
                val constName = file.nameWithoutExtension.toFixtureConstantName()
                val literal = file.readText().toKotlinStringLiteral()
                "    /** Источник: `docs/api/samples/${file.name}`. */\n" +
                    "    val $constName: String = $literal"
            }

        val content =
            buildString {
                appendLine("package $pkg")
                appendLine()
                appendLine("// АВТОГЕНЕРИРУЕМЫЙ ФАЙЛ — не редактировать вручную.")
                appendLine("// Источник: JSON-файлы из docs/api/samples.")
                appendLine("// Генератор: GenerateApiFixturesTask (build-logic/convention), см. её KDoc.")
                appendLine()
                appendLine("/** Содержимое JSON-файлов из `docs/api/samples`, зашитое в код на этапе сборки. */")
                appendLine("object ApiFixtures {")
                append(properties)
                appendLine()
                appendLine("}")
            }

        File(pkgDir, "ApiFixtures.kt").writeText(content)
    }
}

/** `discover_watching_page0` -> `discoverWatchingPage0`. Первая часть — lowercase, остальные — capitalize. */
internal fun String.toFixtureConstantName(): String =
    split('_')
        .filter { it.isNotEmpty() }
        .mapIndexed { index, part -> if (index == 0) part.lowercase() else part.replaceFirstChar { it.uppercaseChar() } }
        .joinToString("")

/**
 * Кодирует произвольную строку как валидный Kotlin string-литерал (обычные `"..."`, НЕ raw-строку)
 * — экранирует `\`, `"`, перевод строки, `\r`, таб и `$`. Устойчиво к любому содержимому файла,
 * включая случайные `"""`-последовательности, которые сломали бы raw-строку.
 */
internal fun String.toKotlinStringLiteral(): String {
    val sb = StringBuilder(length + 16)
    sb.append('"')
    for (c in this) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '$' -> sb.append("\\$")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
    }
    sb.append('"')
    return sb.toString()
}
