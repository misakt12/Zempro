package com.zakazky.app.common.utils

import platform.Foundation.*

// ── iOS implementace DateUtils ────────────────────────────────────────────────

actual fun getCurrentDateString(): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "dd.MM.yyyy"
        locale = NSLocale.currentLocale
    }
    return formatter.stringFromDate(NSDate())
}
