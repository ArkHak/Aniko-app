package com.aniko.app.feature.home

import java.util.Calendar

actual fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
