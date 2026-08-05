plugins {
    id("anix.kmp.library")
}

android {
    namespace = "com.anixkmp.network"
}

kotlin {
    sourceSets {
        // `jvmSharedMain` (Android + Desktop) объявлен в convention-плагине anix.kmp.library.
        val jvmSharedMain by getting

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.serialization.kotlinx.json)
            api(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.logging)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmSharedMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
