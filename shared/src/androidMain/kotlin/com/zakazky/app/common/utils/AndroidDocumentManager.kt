package com.zakazky.app.common.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDocumentManager(onResult: (String, ByteArray) -> Unit): DocumentManager {
    val context = LocalContext.current
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) cursor.getString(nameIndex) else "dokument"
            } else "dokument"
            cursor?.close()
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            if (bytes != null) onResult(name, bytes)
        }
    }
    return remember {
        object : DocumentManager {
            override fun pickDocument() {
                pickLauncher.launch("*/*")
            }
        }
    }
}
