package com.zakazky.app.common.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun getCurrentDateString(): String {
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
}
