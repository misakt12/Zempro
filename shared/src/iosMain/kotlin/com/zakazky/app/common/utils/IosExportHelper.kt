package com.zakazky.app.common.utils

import androidx.compose.runtime.*
import platform.Foundation.*
import platform.UIKit.*

// ── ExportHelper pro iOS: sdílení souboru přes systémový Share Sheet ─────────

class IosExportHelper : ExportHelper {
    override fun exportDocument(
        content: String,
        defaultName: String,
        extension: String,
        mimeType: String
    ) {
        try {
            // Zapíšeme obsah do dočasného souboru
            val tempDir = NSTemporaryDirectory()
            val fileName = "$defaultName.$extension"
            val filePath = "$tempDir$fileName"
            val fileURL = NSURL.fileURLWithPath(filePath)

            val nsString = content as NSString
            nsString.writeToURL(
                url = fileURL,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null
            )

            // Zobrazíme iOS Share Sheet (AirDrop, Mail, Files, Google Drive…)
            val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return

            val activityVC = UIActivityViewController(
                activityItems = listOf(fileURL),
                applicationActivities = null
            )

            // iPad vyžaduje sourceView — na iPhonu se ignoruje
            activityVC.popoverPresentationController?.sourceView = rootVC.view

            rootVC.presentViewController(activityVC, animated = true, completion = null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
actual fun rememberExportHelper(): ExportHelper {
    return remember { IosExportHelper() }
}
