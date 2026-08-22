import com.aniko.buildlogic.GenerateApiFixturesTask

plugins {
    id("aniko.kmp.library")
    id("aniko.kmp.serialization")
    id("aniko.lint")
}

android {
    namespace = "com.aniko.data"
}

// F1 (Фаза 11, docs/REELWAVE_PLAN.md): зашивает docs/api/samples/*.json в сгенерированный
// ApiFixtures.kt для commonTest — см. подробный KDoc GenerateApiFixturesTask про то, почему не
// файловый I/O в рантайме теста.
val generateApiFixtures =
    tasks.register<GenerateApiFixturesTask>("generateApiFixtures") {
        samplesDir.set(rootProject.layout.projectDirectory.dir("docs/api/samples"))
        outputDir.set(layout.buildDirectory.dir("generated/apiFixtures/commonTest/kotlin"))
        packageName.set("com.aniko.data.fixtures")
    }

kotlin {
    sourceSets {
        getByName("commonTest").kotlin.srcDir(generateApiFixtures.flatMap { it.outputDir })

        commonMain.dependencies {
            api(project(":shared:model"))
            api(project(":shared:network"))
            api(project(":shared:database"))
            // `EpisodeRepository.resolvePlaybackSource` возвращает `PlaybackSource` — публичный
            // тип из :shared:player, поэтому api(), а не implementation(): модули, которые
            // зависят от :shared:data и читают этот тип, не обязаны подключать :shared:player сами.
            api(project(":shared:player"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.multiplatform.settings)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            // P10.T1: периодический дренаж офлайн-очереди (AndroidBackgroundSyncScheduler +
            // SyncQueueDrainWorker). implementation() достаточно — WorkManager нужен только внутри
            // этого модуля, а его `androidx.startup`-провайдер попадает в манифест приложения через
            // merge AAR-манифеста и на транзитивной зависимости.
            implementation(libs.androidx.work.runtime)
        }
    }
}
