package com.zakazky.app.common.utils

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

class AndroidExportHelper(private val context: Context) : ExportHelper {
    override fun exportDocument(content: String, defaultName: String, extension: String, mimeType: String) {
        try {
            val cachePath = File(context.cacheDir, "exports")
            cachePath.mkdirs()
            
            val file = File(cachePath, "$defaultName.$extension")
            file.writeText(content)
            
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, defaultName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(Intent.createChooser(intent, "Sdílet Výkaz...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
actual fun rememberExportHelper(): ExportHelper {
    val context = LocalContext.current
    return remember(context) { AndroidExportHelper(context) }
}
