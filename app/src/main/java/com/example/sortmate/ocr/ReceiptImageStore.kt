package com.example.sortmate.ocr

import android.content.Context
import android.net.Uri
import java.io.File

/** Copies the CameraX cache image into app storage so the receipt survives cache eviction. */
object ReceiptImageStore {

    fun copyToInternalStorage(context: Context, source: Uri): String? = runCatching {
        val directory = File(context.filesDir, "receipts").apply { mkdirs() }
        val destination = File(directory, "receipt-${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(source)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open captured image")
        destination.absolutePath
    }.getOrNull()
}
