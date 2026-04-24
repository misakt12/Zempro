package com.zakazky.app.common.utils

import platform.Foundation.*
import platform.UIKit.*

// ── iOS implementace: BitmapUtils ────────────────────────────────────────────

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return Image.makeFromEncoded(this).toComposeImageBitmap()
}
