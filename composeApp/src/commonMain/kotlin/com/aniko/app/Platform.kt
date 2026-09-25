package com.aniko.app

/** Человекочитаемое имя платформы — используется на стартовом экране и в логах. */
expect fun platformName(): String

/**
 * Завершает приложение — нужно только macOS-обновлению: перед заменой `Aniko.app` процесс обязан
 * выйти. На Android/iOS ничего не делает (там такой сценарий не возникает).
 */
expect fun quitApplication()
