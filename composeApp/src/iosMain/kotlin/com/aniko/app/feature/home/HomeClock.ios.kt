package com.aniko.app.feature.home

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun currentHour(): Int {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "HH"
    return formatter.stringFromDate(NSDate()).toIntOrNull() ?: 12
}
