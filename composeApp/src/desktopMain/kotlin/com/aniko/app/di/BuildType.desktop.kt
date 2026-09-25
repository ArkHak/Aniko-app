package com.aniko.app.di

/**
 * Установленное приложение (`.app` из `packageDmg`) запускается нативным лаунчером `jpackage`,
 * который передаёт JVM системное свойство `jpackage.app-path` (проверено по строкам лаунчера из
 * `jdk.jpackage.jmod`: он добавляет `-Djpackage.app-path=`). Его нет при запуске из Gradle
 * (`:composeApp:run`), из IDE и в тестах — это и есть debug.
 */
actual fun isDebugBuild(): Boolean = System.getProperty(JPACKAGE_APP_PATH_PROPERTY) == null

private const val JPACKAGE_APP_PATH_PROPERTY = "jpackage.app-path"
