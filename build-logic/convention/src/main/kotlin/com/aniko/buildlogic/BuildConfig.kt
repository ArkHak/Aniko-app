package com.aniko.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Доступ к version catalog `libs` изнутри precompiled script plugin'ов
 * (сгенерированный accessor `libs` там недоступен — это известное ограничение Gradle).
 */
internal val Project.libsCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("Нет версии '$alias' в libs.versions.toml") }
        .requiredVersion
