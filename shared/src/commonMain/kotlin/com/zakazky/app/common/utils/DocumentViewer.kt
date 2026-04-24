package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable

interface DocumentViewer {
    fun viewDocument(name: String, bytes: ByteArray)
}

@Composable
expect fun rememberDocumentViewer(): DocumentViewer
