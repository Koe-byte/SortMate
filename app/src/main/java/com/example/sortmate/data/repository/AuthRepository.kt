package com.example.sortmate.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Uses Firebase Anonymous Auth so several devices can share a household without
 * building a full account system. Falls back to null when Firebase is not set up.
 */
class AuthRepository(private val firebaseAvailable: Boolean) {

    suspend fun ensureUserId(): String? = withContext(Dispatchers.IO) {
        if (!firebaseAvailable) return@withContext null
        runCatching {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: auth.signInAnonymously().await().user
            user?.uid
        }.onFailure { Log.w(TAG, "Anonymous sign-in unavailable", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
