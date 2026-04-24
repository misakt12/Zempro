package com.zakazky.app.common.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

// Android neumožňuje přímé volání file dialogu z non-Composable funkce.
// Proto zálohu řešíme přes globální state — ProfileScreen zavolá compose launcher.

// Globální callback pro předání výsledku importu zpět do UI
internal var pendingImportCallback: ((String?) -> Unit)? = null
internal var pendingExportData: Pair<String, String>? = null // (jsonData, fileName)

// Compose provider pro backup — musí být zavolán uvnitř composable (ProfileScreen)
@Composable
fun rememberBackupLaunchers(): BackupLaunchers {
    val context = LocalContext.current

    // Export: uloží JSON přes systémový dialog pro výběr umístění
    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val data = pendingExportData
            if (data != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(data.first.toByteArray(Charsets.UTF_8))
                    }
                    android.widget.Toast.makeText(context, "✅ Záloha uložena", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "❌ Chyba zálohy: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                pendingExportData = null
            }
        }
    }

    // Import: otevře file picker pro výběr .json zálohy
    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val callback = pendingImportCallback
        if (uri != null && callback != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                callback(json)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "❌ Chyba obnovy: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                callback(null)
            }
        } else {
            callback?.invoke(null)
        }
        pendingImportCallback = null
    }

    return remember { BackupLaunchers(createDocLauncher, openDocLauncher) }
}

class BackupLaunchers(
    private val createDoc: androidx.activity.result.ActivityResultLauncher<String>,
    private val openDoc: androidx.activity.result.ActivityResultLauncher<String>
) {
    fun export(jsonData: String, fileName: String) {
        pendingExportData = Pair(jsonData, fileName)
        createDoc.launch(fileName)
    }
    fun import(onResult: (String?) -> Unit) {
        pendingImportCallback = onResult
        openDoc.launch("application/json")
    }
}

// Tyto expect funkce jsou na Androidu prázdné — záloha se řeší přes BackupLaunchers Composable
actual fun exportBackup(jsonData: String, fileName: String) {
    // Na Androidu se záloha spouští přes BackupLaunchers Composable — tato funkce se nepoužívá
}

actual fun importBackup(onResult: (String?) -> Unit) {
    // Na Androidu se import spouští přes BackupLaunchers Composable — tato funkce se nepoužívá
}

// Automatické zálohování je jen na PC — na Androidu nedělá nic
actual fun startAutoBackup(getJsonData: () -> String) {
    // No-op na Androidu
}
