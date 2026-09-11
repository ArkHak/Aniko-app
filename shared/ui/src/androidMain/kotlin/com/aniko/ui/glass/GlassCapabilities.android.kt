package com.aniko.ui.glass

import android.os.Build

/** См. KDoc [isRealtimeBlurSupported] в commonMain — `RenderEffect` требует API 31+. */
internal actual fun isRealtimeBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
