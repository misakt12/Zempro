package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*

@Composable
actual fun rememberDocumentViewer(): DocumentViewer {
    return remember {
        object : DocumentViewer {
            override fun viewDocument(name: String, bytes: ByteArray) {
                try {
                    // Uložíme soubor do dočasné složky
                    val tempDir = NSTemporaryDirectory()
                    val filePath = "$tempDir$name"
                    val fileURL = NSURL.fileURLWithPath(filePath)

                    // Zapíšeme bajty do souboru
                    val nsData = bytes.usePinned { pinned ->
                        NSData.create(
                            bytes = pinned.addressOf(0),
                            length = bytes.size.toULong()
                        )
                    }
                    nsData.writeToURL(fileURL, atomically = true)

                    // Otevřeme soubor přes UIDocumentInteractionController
                    // (iOS ekvivalent Android chooseru – nabídne Pages, Google Docs, atd.)
                    val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
                        ?: return

                    val controller = UIDocumentInteractionController.interactionControllerWithURL(fileURL)

                    // Pokud se nedaří otevřít přímo, zobrazí systémový výběr aplikací
                    val presented = controller.presentOpenInMenuFromRect(
                        rect = rootVC.view.bounds,
                        inView = rootVC.view,
                        animated = true
                    )

                    if (!presented) {
                        // Záloha: zkusíme obecný preview
                        controller.presentPreviewAnimated(true)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
