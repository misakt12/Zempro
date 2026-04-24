package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import java.awt.Desktop

@Composable
actual fun rememberDocumentViewer(): DocumentViewer {
    return remember {
        object : DocumentViewer {
            override fun viewDocument(name: String, bytes: ByteArray) {
                try {
                    val tempFile = File(System.getProperty("java.io.tmpdir"), name)
                    tempFile.writeBytes(bytes)
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(tempFile)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
