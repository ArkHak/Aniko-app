import com.aniko.buildlogic.libsCatalog
import com.aniko.buildlogic.version
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

/**
 * ktlint (форматирование/стиль) + detekt (статический анализ) для модуля (P1.T11).
 *
 * Подключает кастомный ruleset "aniko" (P2.T10, модуль `:detekt-rules` — правило
 * `ForbiddenCyrillicStringLiteral` против захардкоженных кириллических строковых литералов вне
 * i18n-слоя) для КАЖДОГО модуля, применяющего `aniko.lint`.
 *
 * Про границу includeBuild (важно, эмпирически проверено — см. журнал изменений плана
 * `docs/REELWAVE_PLAN.md`, запись P2.T10): `build-logic` — отдельная includeBuild-сборка со своим
 * project-графом (`build-logic/settings.gradle.kts` содержит только `:convention`, там нет
 * `:detekt-rules`). Интуитивно кажется, что `project(":detekt-rules")` внутри ЭТОГО файла (он же
 * часть build-logic) не должен резолвиться. Но это не так: precompiled script plugin (этот файл)
 * компилируется в класс `Plugin<Project>`, и `project(...)` внутри его тела — метод получателя
 * `Project`, вызываемый в рантайме на РЕАЛЬНОМ объекте `Project`, к которому плагин применяется
 * (`:composeApp`, `:shared:model`, ...) — а это projects КОРНЕВОЙ сборки, где `:detekt-rules`
 * прекрасно резолвится. Проверено запуском `:shared:model:detektMetadataCommonMain` с реальным
 * кириллическим литералом ТОЛЬКО с этой декларацией (без какой-либо `detektPlugins`-зависимости
 * в самом `shared/model/build.gradle.kts`) — правило сработало. Поэтому одной декларации здесь
 * достаточно для всех потребителей `aniko.lint`, дублировать её в каждом модуле не нужно.
 *
 * `config/detekt/detekt.yml` в корне репозитория настраивает один встроенный parameter
 * (`FunctionNaming.ignoreAnnotated`) и конфигурирует правило `ForbiddenCyrillicStringLiteral`
 * (excludes на тестовые source set'ы и i18n-слой).
 *
 * При первом включении (2026-08-07) detekt на дефолтных правилах нашёл ~70 существующих
 * находок (MagicNumber в цветовой палитре/status-кодах, TooGenericExceptionCaught,
 * ForbiddenComment на TODO, InjectDispatcher, LongMethod/LongParameterList/TooManyFunctions и
 * т.п.) — по инструкции задачи P1.T11 они не гасятся отключением правил, а грандфазерятся через
 * официальный механизм detekt (`detektBaseline*`, файл `config/detekt/baseline.xml` на модуль):
 * существующий код не блокирует CI, а новый код проверяется без послаблений.
 */
plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    // :detekt-rules — модуль КОРНЕВОЙ сборки (не build-logic), см. подробный KDoc в
    // detekt-rules/build.gradle.kts про то, почему он объявлен именно там.
    add("detektPlugins", project(":detekt-rules"))
}

ktlint {
    reporters {
        reporter(ReporterType.PLAIN)
    }
    // Compose Resources генерирует Kotlin в build/generated/... (ActualResourceCollectors.kt
    // и т.п.) — это не наш код, ktlint по умолчанию сканирует все kotlin.srcDirs source set'а,
    // включая generated, поэтому исключаем явно.
    filter {
        exclude { entry -> entry.file.path.contains("${File.separator}generated${File.separator}") }
    }
}

detekt {
    // Встроенный дефолтный ruleset detekt + один донастроенный параметр (см. KDoc выше).
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    // Свой baseline на каждый модуль — находки commonMain/androidMain/desktopMain/iosMain модуля
    // отличаются, единый baseline на весь репозиторий смешивал бы их без всякой пользы.
    baseline = project.layout.projectDirectory.file("config/detekt/baseline.xml").asFile
}

// detekt 1.23.8 не понимает JDK новее 22 (падает на попытке распарсить версию текущей JVM,
// например "25.0.3" на машине с локально запиненным современным JDK для Gradle-демона — см.
// gradle-daemon-jvm.properties, который в .gitignore и не часть репозитория). Поэтому detekt
// намеренно запускаем не на демоновской JVM, а на JDK, резолвнутом через Java Toolchain под
// jvmTarget всей сборки (17). У этого репозитория НЕТ `org.gradle.toolchains.foojay-resolver-
// convention` (или другого toolchain-резолвера) в settings.gradle.kts, поэтому Gradle не умеет
// САМ докачать недостающий JDK — если подходящего JDK 17 нет ни локально, ни через
// `JAVA_HOME`/системные пути, `javaToolchains.launcherFor(...)` упадёт с "No compatible
// toolchains found". Поэтому в CI (.github/workflows/ci.yml) JDK 17 ставится явно через
// `actions/setup-java@v4` — рассчитывать на auto-provisioning здесь нельзя.
val detektJdkLauncher =
    project.extensions.getByType(JavaToolchainService::class.java).launcherFor {
        languageVersion.set(JavaLanguageVersion.of(libsCatalog.version("jvmTarget")))
    }

// Одинаковая донастройка нужна и обычным `Detekt`-таскам (проверка), и `DetektCreateBaselineTask`
// (генерация baseline из-под `detektBaseline*`) — у обоих одинаковый набор свойств
// jvmTarget/jdkHome/exclude, но общего предка с этими свойствами у них нет, кроме `SourceTask`.
tasks.withType<Detekt>().configureEach {
    jvmTarget = libsCatalog.version("jvmTarget")
    jdkHome.fileProvider(detektJdkLauncher.map { it.metadata.installationPath.asFile })
    // Тот же повод, что и у ktlint-фильтра выше: Compose Resources генерирует Kotlin в
    // build/generated/... — не наш код, детектить там нечего. Простой glob-`exclude("**/build/
    // generated/**")` тут не матчится: у сгенерированного source set'а корень сам лежит внутри
    // build/generated, а `exclude` сравнивает путь относительно корня source set'а, не абсолютный —
    // поэтому, как и в ktlint-фильтре выше, сравниваем по абсолютному пути файла.
    exclude { entry -> entry.file.path.contains("${File.separator}generated${File.separator}") }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = libsCatalog.version("jvmTarget")
    jdkHome.fileProvider(detektJdkLauncher.map { it.metadata.installationPath.asFile })
    exclude { entry -> entry.file.path.contains("${File.separator}generated${File.separator}") }
}
