package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable

interface DocumentManager {
    fun pickDocument()
}

@Composable
expect fun rememberDocumentManager(onResult: (String, ByteArray) -> Unit): DocumentManager
