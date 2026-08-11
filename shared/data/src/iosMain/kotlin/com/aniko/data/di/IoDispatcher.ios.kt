package com.aniko.data.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** iOS — `Dispatchers.IO` internal на этой версии coroutines, см. KDoc expect-объявления. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
