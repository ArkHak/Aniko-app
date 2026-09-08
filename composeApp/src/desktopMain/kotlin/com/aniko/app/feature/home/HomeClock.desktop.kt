package com.aniko.app.feature.home

import java.time.LocalTime

actual fun currentHour(): Int = LocalTime.now().hour
