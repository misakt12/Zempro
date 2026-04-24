package com.zakazky.app.common.utils

import android.content.Context
import android.media.RingtoneManager
import java.io.File
import android.util.Base64

actual class PlatformStorage(val context: Context)

actual fun writePlatformData(storage: PlatformStorage, json: String) {
    try {
        val file = File(storage.context.filesDir, "zempro_database.json")
        file.writeText(json)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun readPlatformData(storage: PlatformStorage): String? {
    try {
        val file = File(storage.context.filesDir, "zempro_database.json")
        if (file.exists()) {
            return file.readText()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

actual fun encodeBase64(byteArray: ByteArray): String {
    // NO_WRAP = bez \n na konci řádků, kompatibilní se všemi dekoderami
    return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
}

actual fun decodeBase64(string: String): ByteArray {
    // Přijímáme oba formáty: starý (DEFAULT s \n) i nový (NO_WRAP)
    return android.util.Base64.decode(string.trim(), android.util.Base64.DEFAULT)
}

actual fun printInvoice(htmlContent: String, taskName: String) {
    println("Print feature not fully implemented on Android yet without Context. Task: $taskName")
}

actual fun openEmailClient(to: String, subject: String, body: String, attachmentHtml: String?, taskName: String) {
    println("Email feature not fully implemented on Android yet without Context. To: $to, Subject: $subject")
}

actual fun getCurrentTimestamp(): Long {
    return System.currentTimeMillis()
}

actual fun formatTimestamp(timestamp: Long): String {
    val formatter = java.text.SimpleDateFormat("dd.MM. yyyy HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timestamp))
}

/**
 * Přehraje systémový zvuk upozornění Android.
 * Nevyžaduje žádná extra oprávnění — používá výchozí notification ringtone.
 */
actual fun playNotificationSound(storage: PlatformStorage) {
    try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(storage.context, uri)
        ringtone?.play()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
