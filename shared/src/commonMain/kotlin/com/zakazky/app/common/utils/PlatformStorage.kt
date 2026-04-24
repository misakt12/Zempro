package com.zakazky.app.common.utils

expect class PlatformStorage

expect fun writePlatformData(storage: PlatformStorage, json: String)
expect fun readPlatformData(storage: PlatformStorage): String?

expect fun encodeBase64(byteArray: ByteArray): String
expect fun decodeBase64(string: String): ByteArray

expect fun printInvoice(htmlContent: String, taskName: String)
expect fun openEmailClient(to: String, subject: String, body: String, attachmentHtml: String?, taskName: String)

expect fun getCurrentTimestamp(): Long
expect fun formatTimestamp(timestamp: Long): String

/** Přehraje krátký zvukový tón — upozornění pro mechanika o nové zakázce */
expect fun playNotificationSound(storage: PlatformStorage)
