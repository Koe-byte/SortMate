package com.example.sortmate.ocr

import android.content.Context
import android.net.Uri
import com.example.sortmate.domain.ReceiptParser
import com.example.sortmate.domain.ReceiptScanResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class ReceiptOcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(context: Context, imageUri: Uri): ReceiptScanResult {
        val image = InputImage.fromFilePath(context, imageUri)
        val text = recognizer.process(image).await()
        return ReceiptParser.parse(text.text)
    }

    fun close() {
        recognizer.close()
    }
}
