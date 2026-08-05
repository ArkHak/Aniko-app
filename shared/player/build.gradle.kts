plugins {
    id("anix.kmp.library")
    id("anix.kmp.compose")
}

android {
    namespace = "com.anixkmp.player"
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
