package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberMediaManager(onResult: (ByteArray?) -> Unit): MediaManager {
    return remember {
        object : MediaManager {
            override fun takePicture() {
                pickImage()
            }
            
            override fun pickImage() {
                val dialog = FileDialog(null as Frame?, "Vyberte fotografii", FileDialog.LOAD)
                dialog.file = "*.jpg;*.jpeg;*.png;*.bmp"
                dialog.isVisible = true
                
                if (dialog.file != null) {
                    val file = File(dialog.directory, dialog.file)
                    onResult(file.readBytes())
                } else {
                    onResult(null)
                }
            }
        }
    }
}
