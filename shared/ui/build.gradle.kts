plugins {
    id("aniko.kmp.library")
    id("aniko.kmp.compose")
}

android {
    namespace = "com.aniko.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:model"))

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.components.resources)
            api(compose.ui)

            api(libs.coil.compose)
            api(libs.coil.network.ktor3)
            implementation(libs.ktor.client.core)
        }
    }
}
