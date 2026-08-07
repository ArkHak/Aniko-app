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
 * Дефолтные ruleset'ы без кастомных правил: собственное detekt-правило против
 * захардкоженных строковых литералов в UI — это P2.T10, отдельная будущая задача,
 * сюда сознательно не входит. `config/detekt/detekt.yml` в корне репозитория лишь донастраивает
 * один встроенный parameter (`FunctionNaming.ignoreAnnotated`), кастомных Rule-классов нет.
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
