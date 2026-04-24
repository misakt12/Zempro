package com.zakazky.app.common.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberMediaManager(onResult: (ByteArray?) -> Unit): MediaManager {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // GUARD: Zabraní zpracování výsledku dvakrát (double-click na soubor v prickeru způsoboval crash)
    var isProcessing by remember { mutableStateOf(false) }
    
    // Optimalizační funkce: Zmenší obrovskou fotku (až 20MB) z fotoaparátu na cca 100-300KB
    // Spouštíme na pozadí (IO vlákno), aby neblokovala UI a nezpůsobovala ANR / OOM
    suspend fun compressImage(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return@withContext null
                val out = ByteArrayOutputStream()
                val maxWidth = 1000 // Optimální šířka pro úsporu databáze
                var scaled = bitmap
                if (bitmap.width > maxWidth || bitmap.height > maxWidth) {
                    val ratioWidth = maxWidth.toFloat() / bitmap.width
                    val ratioHeight = maxWidth.toFloat() / bitmap.height
                    val ratio = minOf(ratioWidth, ratioHeight)
                    val newWidth = (bitmap.width * ratio).toInt()
                    val newHeight = (bitmap.height * ratio).toInt()
                    scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                }
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, out) // 70% kvalita = perfektní poměr váha/výkon
                out.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Launcher pro výběr fotky z galerie
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (isProcessing) return@rememberLauncherForActivityResult // GUARD: zabrání double-processing
        isProcessing = true
        if (uri != null) {
            scope.launch {
                val bytes = compressImage(uri)
                withContext(Dispatchers.Main) {
                    onResult(bytes)
                    isProcessing = false
                }
            }
        } else {
            onResult(null)
            isProcessing = false
        }
    }
    
    // Záchytné Uri pro ukládání pořízené fotky fotoaparátem
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Launcher pro zapnutí fotoaparátu
    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (isProcessing) return@rememberLauncherForActivityResult // GUARD
        isProcessing = true
        if (success && tempPhotoUri != null) {
            scope.launch {
                val bytes = compressImage(tempPhotoUri!!)
                withContext(Dispatchers.Main) {
                    onResult(bytes)
                    isProcessing = false
                }
            }
        } else {
            onResult(null)
            isProcessing = false
        }
    }
    
    return remember {
        object : MediaManager {
            override fun takePicture() {
                // Připravit speciální bezpečný soubor přes FileProvider, kam kamera nasype bity
                val file = File(context.cacheDir, "camera_photo_${UUID.randomUUID()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                tempPhotoUri = uri
                takePhotoLauncher.launch(uri)
            }
            
            override fun pickImage() {
                if (!isProcessing) { // Zabrání otevření galerie dvakrát najednou
                    pickLauncher.launch("image/*")
                }
            }
        }
    }
}

