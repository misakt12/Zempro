package com.zakazky.app.common.utils

import platform.Foundation.*
import platform.UIKit.*

// ── iOS BackupManager ────────────────────────────────────────────────────────
// iOS používá UIDocumentPickerViewController pro import/export přes Files app.

// Globální callback pro import zálohy
private var pendingIosImportCallback: ((String?) -> Unit)? = null

// Export zálohy: zapíšeme JSON do dočasného souboru a zobrazíme Share Sheet
actual fun exportBackup(jsonData: String, fileName: String) {
    try {
        val tempDir = NSTemporaryDirectory()
        val filePath = "$tempDir$fileName"
        val fileURL = NSURL.fileURLWithPath(filePath)

        val nsString = jsonData as NSString
        nsString.writeToURL(
            url = fileURL,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )

        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return

        val activityVC = UIActivityViewController(
            activityItems = listOf(fileURL),
            applicationActivities = null
        )
        activityVC.popoverPresentationController?.sourceView = rootVC.view

        rootVC.presentViewController(activityVC, animated = true, completion = null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Import zálohy: otevřeme picker pro výběr JSON souboru
actual fun importBackup(onResult: (String?) -> Unit) {
    try {
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: run {
            onResult(null)
            return
        }
        pendingIosImportCallback = onResult

        // UIDocumentPickerViewController — podporuje iOS 11+
        @Suppress("DEPRECATION")
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.json", "public.text", "public.data"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen
        )
        picker.delegate = IosBackupPickerDelegate.shared
        picker.allowsMultipleSelection = false

        rootVC.presentViewController(picker, animated = true, completion = null)
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(null)
    }
}

// Na iOS není automatické pozadí — zálohu řeší uživatel ručně přes Share Sheet
actual fun startAutoBackup(getJsonData: () -> String) {
    // No-op na iOS — záloha probíhá ručně přes exportBackup()
}

// ── Delegate pro import zálohy ────────────────────────────────────────────────
class IosBackupPickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    companion object {
        val shared = IosBackupPickerDelegate()
    }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: run {
            pendingIosImportCallback?.invoke(null)
            pendingIosImportCallback = null
            return
        }

        url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url)
            val json = data?.let {
                NSString.create(data = it, encoding = NSUTF8StringEncoding) as? String
            }
            pendingIosImportCallback?.invoke(json)
        } finally {
            url.stopAccessingSecurityScopedResource()
            pendingIosImportCallback = null
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        pendingIosImportCallback?.invoke(null)
        pendingIosImportCallback = null
    }
}
