plugins {
    `kotlin-dsl`
}

group = "com.aniko.buildlogic"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // `implementation`, а не `compileOnly`: precompiled script plugins применяют эти
    // плагины через `plugins { id(...) }`, значит они нужны и в runtime-classpath build-logic.
    implementation(libs.plugin.android.gradle)
    implementation(libs.plugin.kotlin.multiplatform)
    implementation(libs.plugin.kotlin.serialization)
    implementation(libs.plugin.compose.compiler)
    implementation(libs.plugin.compose.multiplatform)
    implementation(libs.plugin.ktlint)
    implementation(libs.plugin.detekt)
    implementation(libs.plugin.sqldelight)
}
