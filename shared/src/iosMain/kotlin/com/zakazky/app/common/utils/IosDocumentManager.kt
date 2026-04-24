@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.zakazky.app.common.utils

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.darwin.NSObject
import platform.posix.memcpy

// ── Globální callback ────────────────────────────────────────────────────────
private var pendingDocumentCallback: ((String, ByteArray) -> Unit)? = null

// ── UIDocumentPickerViewController delegate ──────────────────────────────────
private class DocumentPickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: run {
            pendingDocumentCallback = null
            return
        }

        // Musíme požádat o bezpečnostní scope přístupu (sandbox)
        url.startAccessingSecurityScopedResource()
        try {
            val fileName = url.lastPathComponent ?: "dokument"
            val data = NSData.dataWithContentsOfURL(url) ?: run {
                pendingDocumentCallback = null
                return
            }
            val length = data.length.toInt()
            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length.toULong())
            }
            pendingDocumentCallback?.invoke(fileName, bytes)
        } finally {
            url.stopAccessingSecurityScopedResource()
            pendingDocumentCallback = null
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        pendingDocumentCallback = null
    }
}

private val documentPickerDelegate = DocumentPickerDelegate()

@Composable
actual fun rememberDocumentManager(onResult: (String, ByteArray) -> Unit): DocumentManager {
    val callbackRef = rememberUpdatedState(onResult)

    return remember {
        object : DocumentManager {
            private fun rootViewController(): UIViewController? =
                UIApplication.sharedApplication.keyWindow?.rootViewController

            override fun pickDocument() {
                val root = rootViewController() ?: return
                pendingDocumentCallback = { name, bytes -> callbackRef.value(name, bytes) }

                // Povolíme všechny typy dokumentů (PDF, DOCX, obrázky…)
                val version = cValue<NSOperatingSystemVersion> {
                    majorVersion = 14
                    minorVersion = 0
                    patchVersion = 0
                }
                
                val picker = if (NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(version)) {
                    // iOS 14+ — UTType API
                    UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
                } else {
                    @Suppress("DEPRECATION")
                    UIDocumentPickerViewController(documentTypes = listOf("public.item"), inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen)
                }

                picker.delegate = documentPickerDelegate
                picker.allowsMultipleSelection = false
                root.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}
