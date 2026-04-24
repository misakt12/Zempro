@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.zakazky.app.common.utils

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.*
import platform.Photos.*
import platform.darwin.NSObject
import platform.posix.memcpy

// ── Globální callback pro předání výsledku zpět do Compose ───────────────────
private var pendingMediaCallback: ((ByteArray?) -> Unit)? = null

// ── UIImagePickerController delegate ────────────────────────────────────────
@OptIn(ExperimentalForeignApi::class)
private class ImagePickerDelegate : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true, null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image != null) {
            val bytes = compressUIImage(image)
            pendingMediaCallback?.invoke(bytes)
        } else {
            pendingMediaCallback?.invoke(null)
        }
        pendingMediaCallback = null
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        pendingMediaCallback?.invoke(null)
        pendingMediaCallback = null
    }
}

// ── Pomocná funkce: komprese UIImage na JPEG ByteArray (~100-300 KB) ─────────
@OptIn(ExperimentalForeignApi::class)
private fun compressUIImage(image: UIImage): ByteArray? {
    val maxDimension = 1000.0
    val size = image.size
    val w = size.useContents { width }
    val h = size.useContents { height }

    // Přepočítáme rozměry se zachováním poměru stran
    val scaled: UIImage
    if (w > maxDimension || h > maxDimension) {
        val ratio = if (w > h) maxDimension / w else maxDimension / h
        val newW = w * ratio
        val newH = h * ratio
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(newW, newH), false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, newW, newH))
        scaled = UIGraphicsGetImageFromCurrentImageContext() ?: image
        UIGraphicsEndImageContext()
    } else {
        scaled = image
    }

    val nsData = UIImageJPEGRepresentation(scaled, 0.7) ?: return null
    val length = nsData.length.toInt()
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), nsData.bytes, nsData.length.toULong())
    }
    return bytes
}

// ── Singleton delegate (musí žít po celou dobu presentace pickeru) ───────────
private val imagePickerDelegate = ImagePickerDelegate()

@Composable
actual fun rememberMediaManager(onResult: (ByteArray?) -> Unit): MediaManager {
    // Zachytíme aktuální callback přes SideEffect, aby byl vždy aktuální
    val callbackRef = rememberUpdatedState(onResult)

    return remember {
        object : MediaManager {
            private fun rootViewController(): UIViewController? =
                UIApplication.sharedApplication.keyWindow?.rootViewController

            override fun takePicture() {
                val root = rootViewController() ?: return
                if (!UIImagePickerController.isSourceTypeAvailable(
                        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                    )
                ) {
                    // Simulátor nebo zařízení bez kamery — fallback na galerii
                    pickImage()
                    return
                }
                pendingMediaCallback = { bytes -> callbackRef.value(bytes) }
                val picker = UIImagePickerController().apply {
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                    delegate = imagePickerDelegate
                    allowsEditing = false
                }
                root.presentViewController(picker, animated = true, completion = null)
            }

            override fun pickImage() {
                val root = rootViewController() ?: return
                pendingMediaCallback = { bytes -> callbackRef.value(bytes) }
                val picker = UIImagePickerController().apply {
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    delegate = imagePickerDelegate
                    allowsEditing = false
                }
                root.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}
