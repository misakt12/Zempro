package com.zakazky.app.common.utils

import java.io.File
import java.util.Base64

actual class PlatformStorage

actual fun writePlatformData(storage: PlatformStorage, json: String) {
    try {
        val userHome = System.getProperty("user.home")
        val file = File(userHome, ".zempro_database.json")
        file.writeText(json)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun readPlatformData(storage: PlatformStorage): String? {
    try {
        val userHome = System.getProperty("user.home")
        val file = File(userHome, ".zempro_database.json")
        if (file.exists()) {
            return file.readText()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

actual fun encodeBase64(byteArray: ByteArray): String {
    return Base64.getEncoder().encodeToString(byteArray)
}

actual fun decodeBase64(string: String): ByteArray {
    // getMimeDecoder() je tolerantní k \n znakům které přidává Android Base64.DEFAULT
    return Base64.getMimeDecoder().decode(string)
}

actual fun printInvoice(htmlContent: String, taskName: String) {
    try {
        val file = File.createTempFile("faktura_${taskName.replace(" ", "_").take(20)}", ".html")
        file.writeText(htmlContent)
        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
            java.awt.Desktop.getDesktop().browse(file.toURI())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun openEmailClient(to: String, subject: String, body: String, attachmentHtml: String?, taskName: String) {
    try {
        val encodedSubject = java.net.URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
        var finalBody = body
        if (attachmentHtml != null) {
            val file = File.createTempFile("faktura_${taskName.replace(" ", "_").take(20)}", ".html")
            file.writeText(attachmentHtml)
            finalBody += "\n\n(Poznámka: Faktura byla uložena do dočasných souborů. Před odesláním případně vytvořte PDF přes funkci Tisk a vložte jako přílohu.)"
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(file.toURI())
            }
        }
        val encodedBody = java.net.URLEncoder.encode(finalBody, "UTF-8").replace("+", "%20")
        
        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MAIL)) {
            val uri = java.net.URI("mailto:$to?subject=$encodedSubject&body=$encodedBody")
            java.awt.Desktop.getDesktop().mail(uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun getCurrentTimestamp(): Long {
    return System.currentTimeMillis()
}

actual fun formatTimestamp(timestamp: Long): String {
    val formatter = java.text.SimpleDateFormat("dd.MM. yyyy HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timestamp))
}

/**
 * Přehraje krátký "ping" tón na desktopu.
 * Generuje 880Hz sinus vlnu pomocí javax.sound.sampled — bez externích souborů.
 */
actual fun playNotificationSound(storage: PlatformStorage) {
    try {
        val sampleRate = 44100f
        val durationMs = 350
        val frequency = 880.0  // A5 — příjemný zvuk upozornění
        val numSamples = (sampleRate * durationMs / 1000).toInt()
        val buffer = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            // Fade in prvních 10% a fade out posledních 30% — plynulý tón bez cvaknutí
            val fadeIn  = if (i < numSamples * 0.1) i / (numSamples * 0.1) else 1.0
            val fadeOut = if (i > numSamples * 0.7) 1.0 - (i - numSamples * 0.7) / (numSamples * 0.3) else 1.0
            val amplitude = 0.6 * fadeIn * fadeOut
            val sample = (amplitude * Short.MAX_VALUE * Math.sin(2.0 * Math.PI * frequency * i / sampleRate)).toInt().toShort()
            buffer[i * 2]     = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = (sample.toInt() shr 8 and 0xFF).toByte()
        }

        val format = javax.sound.sampled.AudioFormat(sampleRate, 16, 1, true, false)
        val info   = javax.sound.sampled.DataLine.Info(javax.sound.sampled.SourceDataLine::class.java, format)
        val line   = javax.sound.sampled.AudioSystem.getLine(info) as javax.sound.sampled.SourceDataLine
        line.open(format)
        line.start()
        line.write(buffer, 0, buffer.size)
        line.drain()
        line.close()
    } catch (e: Exception) {
        // Fallback — systémový beep pokud something selže
        try { java.awt.Toolkit.getDefaultToolkit().beep() } catch (_: Exception) { }
        e.printStackTrace()
    }
}
