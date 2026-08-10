plugins {
    id("aniko.kmp.library")
    id("aniko.kmp.sqldelight")
    id("aniko.lint")
}

android {
    namespace = "com.aniko.database"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:model"))
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }

        getByName("desktopMain").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }

        getByName("desktopTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }

        getByName("androidUnitTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
