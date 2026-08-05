plugins {
    id("anix.kmp.library")
    id("anix.kmp.serialization")
}

android {
    namespace = "com.anixkmp.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:model"))
            api(project(":shared:network"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.multiplatform.settings)
        }
    }
}
