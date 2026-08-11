package com.aniko.data.di

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Диспетчер для блокирующих IO-операций (SQLDelight JDBC/SQLite-драйверы, файловый доступ).
 *
 * `Dispatchers.IO` нельзя использовать напрямую в `commonMain`: на Kotlin/Native (iOS) в
 * подключённой версии kotlinx.coroutines он объявлен `internal` в `Dispatchers`, а не `public`
 * (подтверждено ошибкой компиляции `:shared:data:compileKotlinIosSimulatorArm64` — "Cannot access
 * 'val IO: CoroutineDispatcher': it is internal"). expect/actual даёт платформенно-подходящий
 * диспетчер: JVM (Android/Desktop) — настоящий эластичный IO-пул, iOS — `Dispatchers.Default`
 * (на Native отдельного аналога IO-пула в этой версии coroutines нет).
 */
expect val ioDispatcher: CoroutineDispatcher
