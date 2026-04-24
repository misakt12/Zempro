package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable

interface MediaManager {
    fun takePicture()
    fun pickImage()
}

@Composable
expect fun rememberMediaManager(onResult: (ByteArray?) -> Unit): MediaManager
