plugins {
    id("aniko.kmp.library")
    id("aniko.kmp.compose")
}

android {
    namespace = "com.aniko.player"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:model"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
