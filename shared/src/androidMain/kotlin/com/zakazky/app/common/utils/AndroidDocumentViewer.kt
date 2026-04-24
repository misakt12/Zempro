package com.zakazky.app.common.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
actual fun rememberDocumentViewer(): DocumentViewer {
    // Zachytíme context UVNITŘ composable – správný Activity context
    val context = LocalContext.current

    return remember(context) {
        object : DocumentViewer {
            override fun viewDocument(name: String, bytes: ByteArray) {
                try {
                    Log.d("DocViewer", "Otevírám dokument: $name (${bytes.size} bytů)")

                    // Uložení do cache
                    val tempFile = File(context.cacheDir, name)
                    tempFile.writeBytes(bytes)
                    Log.d("DocViewer", "Soubor uložen: ${tempFile.absolutePath}")

                    // Získáme FileProvider URI
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    Log.d("DocViewer", "FileProvider URI: $uri")

                    // Určíme MIME typ podle přípony
                    val extension = name.substringAfterLast('.', "").lowercase()
                    val mimeType = when (extension) {
                        "pdf"        -> "application/pdf"
                        "doc"        -> "application/msword"
                        "docx"       -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        "xls"        -> "application/vnd.ms-excel"
                        "xlsx"       -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        "txt"        -> "text/plain"
                        "png"        -> "image/png"
                        "jpg", "jpeg"-> "image/jpeg"
                        else         -> "*/*"
                    }
                    Log.d("DocViewer", "MIME typ: $mimeType")

                    // Základní intent s URI a přístupovými právy
                    val baseIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }

                    // Test schopnosti otevřít daný MIME typ (bez URI – jen typ)
                    fun canHandleIntent(pkg: String): Boolean {
                        return try {
                            val testIntent = Intent(Intent.ACTION_VIEW).apply {
                                type = mimeType
                                setPackage(pkg)
                            }
                            val activities = context.packageManager.queryIntentActivities(
                                testIntent, PackageManager.MATCH_DEFAULT_ONLY
                            )
                            val result = activities.isNotEmpty()
                            Log.d("DocViewer", "canHandleIntent($pkg) = $result (${activities.size} aktivit)")
                            result
                        } catch (e: Exception) {
                            Log.w("DocViewer", "canHandleIntent($pkg) vyvolal výjimku: ${e.message}")
                            false
                        }
                    }

                    when {
                        // Google Docs editor – zdarma, ideální pro .docx
                        canHandleIntent("com.google.android.apps.docs.editors.docs") -> {
                            Log.d("DocViewer", "Otevírám přes Google Docs editor")
                            baseIntent.setPackage("com.google.android.apps.docs.editors.docs")
                            context.startActivity(baseIntent)
                        }
                        // Systémový výběr – zobrazí všechny aplikace co to zvládnou
                        else -> {
                            Log.d("DocViewer", "Otevírám systémový chooser")
                            baseIntent.setPackage(null)
                            val chooser = Intent.createChooser(baseIntent, "Otevřít dokument pomocí...")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("DocViewer", "CHYBA při otevírání dokumentu: ${e.javaClass.simpleName}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}
