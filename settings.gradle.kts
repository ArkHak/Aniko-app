rootProject.name = "Aniko"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// Кастомное detekt-правило (P2.T10) — модуль КОРНЕВОЙ сборки (не build-logic), см. подробный
// KDoc в detekt-rules/build.gradle.kts про то, почему именно так из-за границы includeBuild.
include(":detekt-rules")
include(":shared:model")
include(":shared:network")
include(":shared:data")
include(":shared:player")
include(":shared:ui")
include(":composeApp")
