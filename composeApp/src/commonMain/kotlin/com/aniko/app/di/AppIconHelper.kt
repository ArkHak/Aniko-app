package com.aniko.app.di

/**
 * Переключатель системной иконки лаунчера (P16.T21).
 *
 * Реальную работу делает только Android-реализация ([AndroidAppIconHelper] в `androidMain`) —
 * там иконка приложения физически завязана на `activity-alias` в `AndroidManifest.xml`, и
 * переключение идёт через `PackageManager.setComponentEnabledSetting`. Desktop/iOS не поддерживают
 * несколько иконок одного приложения на уровне ОС так же гибко — там регистрируется
 * [NoOpAppIconHelper] с пустым [supportedIcons], и `SettingsScreen` скрывает секцию выбора
 * иконки, когда список пуст (единственная точка ветвления по платформе — здесь, не в UI).
 */
interface AppIconHelper {
    /** Ключи иконок, которые эта платформа умеет применять; пусто — секция в UI скрыта. */
    val supportedIcons: List<String>

    /** Применяет иконку [iconKey] (один из [supportedIcons]) как активную иконку лаунчера. */
    fun apply(iconKey: String?)
}

/** Платформы без поддержки переключения иконки лаунчера (Desktop, iOS). */
class NoOpAppIconHelper : AppIconHelper {
    override val supportedIcons: List<String> = emptyList()

    override fun apply(iconKey: String?) = Unit
}
