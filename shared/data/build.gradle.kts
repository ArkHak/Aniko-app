plugins {
    id("aniko.kmp.library")
    id("aniko.kmp.serialization")
    id("aniko.lint")
}

android {
    namespace = "com.aniko.data"
}

kotlin {
    sourceSets {
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
        }
    }
}
