// compose.uiTest (F2, Фаза 11) — экспериментальный accessor Compose Multiplatform, нужен для
// runComposeUiTest в composeApp/src/desktopTest/.../smoke/AnikoSmokeHarness.kt.
@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import com.aniko.buildlogic.GenerateApiFixturesTask
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("aniko.kmp.application")
    id("aniko.kmp.compose")
    // нужен для type-safe навигации (@Serializable destinations)
    id("aniko.kmp.serialization")
    id("aniko.lint")
}

val desktopMainClass = "com.aniko.app.MainKt"

// F1 (Фаза 11, docs/REELWAVE_PLAN.md): та же кодогенерация, что и :shared:data:generateApiFixtures
// (см. её KDoc в GenerateApiFixturesTask) — отдельный вызов задачи со своим пакетом, потому что
// KMP/Gradle не даёт чисто шарить commonTest/desktopTest-исходники между модулями без
// testFixtures-инфраструктуры. Смоук-harness composeApp (F2/F3, пакет com.aniko.app.smoke)
// читает эти фикстуры напрямую из com.aniko.app.smoke.fixtures.ApiFixtures.
val generateSmokeApiFixtures =
    tasks.register<GenerateApiFixturesTask>("generateSmokeApiFixtures") {
        samplesDir.set(rootProject.layout.projectDirectory.dir("docs/api/samples"))
        outputDir.set(layout.buildDirectory.dir("generated/apiFixtures/desktopTest/kotlin"))
        packageName.set("com.aniko.app.smoke.fixtures")
    }

kotlin {
    // Чтобы работал и `:composeApp:run` (Compose Desktop), и `:composeApp:desktopRun` (KGP).
    jvm("desktop") {
        mainRun {
            mainClass.set(desktopMainClass)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.aniko.app.composeapp")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:model"))
            implementation(project(":shared:network"))
            implementation(project(":shared:database"))
            implementation(project(":shared:data"))
            implementation(project(":shared:player"))
            implementation(project(":shared:ui"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.multiplatform.settings)

            implementation(libs.material3.adaptive)
            implementation(libs.material3.adaptive.layout)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }

        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            // VLCJ (Step 2/3, P8.T1 пересмотр) живёт целиком в `:shared:player`
            // (`EmbedPlayer.desktop.kt`) как `implementation`-зависимость — composeApp сам не
            // ссылается ни на один тип `uk.co.caprica.*`, отдельная зависимость здесь не нужна.
        }

        // F2/F3 (Фаза 11): смоук-harness — см. KDoc `composeApp/src/desktopTest/.../smoke/`.
        // desktopTest, а НЕ commonTest: `runComposeUiTest` из commonTest компилируется и в
        // androidUnitTest, где без Robolectric он падает — см. KDoc AnikoSmokeHarness.kt.
        getByName("desktopTest").kotlin.srcDir(generateSmokeApiFixtures.flatMap { it.outputDir })
        getByName("desktopTest").dependencies {
            implementation(compose.uiTest)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
            // In-memory SqlDriver для fakeInfraModule (F3) — тот же артефакт, что desktopMain/
            // desktopTest :shared:database, но composeApp его сам не тянет (implementation там,
            // не api, и это main-classpath, не test).
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

// Смоук-тесты desktopTest (P11, src/desktopTest/.../smoke) доводят композицию до PlayerScreen,
// чей desktop-actual (`EmbedPlayer.desktop.kt`, :shared:player) создаёт настоящий VLCJ
// `CallbackMediaPlayerComponent`. На CI (ubuntu, без libVLC) NativeDiscovery vlcj зацикливается
// в обходе дерева каталогов на EDT — тест формально зелёный, но его тестовая JVM потом не
// завершается никогда, и CI-джоб вис до системного 6-часового таймаута (2026-09-15/17,
// диагностировано jstack-дампом). Флаг переводит EmbedPlayerView в режим placeholder:
// без нативного плеера и без реального сетевого резолва потока из тестов.
tasks.withType<Test>().configureEach {
    systemProperty("aniko.playerTestMode", "true")
}

android {
    namespace = "com.aniko.app"

    defaultConfig {
        applicationId = "com.aniko.app"
        versionCode = 2
        versionName = "0.0.2"
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    // P12.T2 (docs/REELWAVE_PLAN.md): релизная подпись. Два источника значений — локальный
    // `keystore.properties` (репо-корень, в .gitignore, см. `keystore.properties.example`) для
    // разработки, переменные окружения ANIKO_KEYSTORE_*/ANIKO_KEY_* для CI (GitHub Secrets,
    // P12.T3) — ни ключ, ни пароли никогда не попадают в git. Без обоих источников
    // `assembleRelease`/`bundleRelease` падает на этапе подписи — намеренно: релиз без ключа не
    // должен тихо собраться неподписанным. `assembleDebug` (CI, повседневная разработка) этот
    // блок не трогает — debug использует автогенерируемый debug-keystore AGP, как раньше.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties =
        Properties().apply {
            if (keystorePropertiesFile.exists()) {
                load(FileInputStream(keystorePropertiesFile))
            }
        }

    fun keystoreValue(
        propertyKey: String,
        envKey: String,
    ): String? = keystoreProperties.getProperty(propertyKey) ?: System.getenv(envKey)

    signingConfigs {
        create("release") {
            keystoreValue("storeFile", "ANIKO_KEYSTORE_PATH")?.let { storeFile = rootProject.file(it) }
            storePassword = keystoreValue("storePassword", "ANIKO_KEYSTORE_PASSWORD")
            keyAlias = keystoreValue("keyAlias", "ANIKO_KEY_ALIAS")
            keyPassword = keystoreValue("keyPassword", "ANIKO_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            // P12.T2: включено осознанно (было `false`) — R8 сжимает и обфусцирует байткод,
            // `proguard-rules.pro` держит явные keep-правила для reflection-зависимого кода
            // (Koin DI, kotlinx.serialization). Проверено живым прогоном подписанного release-
            // APK на эмуляторе после включения (см. отчёт задачи в docs/REELWAVE_PLAN.md).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = desktopMainClass

        nativeDistributions {
            // Desktop-таргет собираем только под macOS (согласовано в плане).
            targetFormats(TargetFormat.Dmg)
            packageName = "Aniko"
            // jpackage требует, чтобы первое число версии пакета было >= 1 — это отдельная
            // версия macOS-инсталлятора, не совпадающая с версией приложения (0.0.2).
            packageVersion = "1.0.2"
            // Без этого jpackage/jlink сам определяет нужные JDK-модули по jdeps-анализу
            // байткода — и не видит java.sql: SQLDelight-драйвер (org.xerial:sqlite-jdbc)
            // грузит java.sql.DriverManager через ServiceLoader (META-INF/services), а не
            // прямой ссылкой в байткоде, jdeps такое не ловит. Итог — собранный .dmg падает
            // при старте с NoClassDefFoundError: java/sql/DriverManager (не проявляется через
            // `./gradlew :composeApp:run` — там используется полный системный JDK, обрезка
            // модулей происходит только при упаковке через jpackage). `includeAllModules`
            // отключает обрезку целиком — жертвуем размером инсталлятора ради надёжности:
            // маленький alpha-проект, не оптимизируем размер, а вручную перечислять модули и
            // рисковать повторением той же ошибки при следующей рефлективно грузимой
            // зависимости не стоит того.
            includeAllModules = true
            macOS {
                // Основная иконка приложения (2026-09-10) — та же иллюстрация, что и на
                // Android/iOS, конвертирована в .icns (`iconutil`, см. отчёт задачи в
                // docs/REELWAVE_PLAN.md). Без этого jpackage использует дефолтную иконку кофейной
                // чашки Java.
                iconFile.set(project.file("icons/AppIcon.icns"))
            }
        }
    }
}
