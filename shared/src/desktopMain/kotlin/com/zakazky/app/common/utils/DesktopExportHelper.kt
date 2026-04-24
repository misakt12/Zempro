package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class DesktopExportHelper : ExportHelper {
    override fun exportDocument(content: String, defaultName: String, extension: String, mimeType: String) {
        val dialog = FileDialog(null as Frame?, "Uložit dokument", FileDialog.SAVE)
        dialog.file = "$defaultName.$extension"
        dialog.isVisible = true
        
        if (dialog.file != null) {
            var fileToSave = File(dialog.directory, dialog.file)
            if (!fileToSave.name.endsWith(".$extension", ignoreCase = true)) {
                fileToSave = File(fileToSave.absolutePath + ".$extension")
            }
            
            if (extension.lowercase() == "pdf") {
                // PDF export using built-in Windows Edge command-line rendering
                val tempHtml = File.createTempFile("temp_invoice_${System.currentTimeMillis()}", ".html")
                tempHtml.writeText(content)
                val edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
                if (File(edgePath).exists()) {
                    try {
                        val process = ProcessBuilder(
                            edgePath,
                            "--headless",
                            "--disable-gpu",
                            "--print-to-pdf=${fileToSave.absolutePath}",
                            tempHtml.absolutePath
                        ).start()
                        process.waitFor()
                        
                        // Automatically open the saved PDF for user confirmation
                        if (java.awt.Desktop.isDesktopSupported() && fileToSave.exists()) {
                            java.awt.Desktop.getDesktop().open(fileToSave)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        tempHtml.delete()
                    }
                } else {
                    println("Microsoft Edge not found for PDF generation.")
                }
            } else {
                fileToSave.writeText(content)
            }
        }
    }
}

@Composable
actual fun rememberExportHelper(): ExportHelper {
    return remember { DesktopExportHelper() }
}
