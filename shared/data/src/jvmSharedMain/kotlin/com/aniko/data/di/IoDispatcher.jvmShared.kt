package com.aniko.data.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** JVM (Android/Desktop) — `Dispatchers.IO` публичен и подходит для блокирующих операций. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
