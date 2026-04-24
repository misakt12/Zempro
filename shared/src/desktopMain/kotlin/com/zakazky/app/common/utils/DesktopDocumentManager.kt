package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberDocumentManager(onResult: (String, ByteArray) -> Unit): DocumentManager {
    return remember {
        object : DocumentManager {
            override fun pickDocument() {
                val dialog = FileDialog(null as Frame?, "Vyberte dokument", FileDialog.LOAD)
                dialog.file = "*.docx"
                dialog.isVisible = true
                if (dialog.file != null) {
                    val file = File(dialog.directory, dialog.file)
                    onResult(file.name, file.readBytes())
                }
            }
        }
    }
}
