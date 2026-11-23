package com.example.sortmate.data.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

/**
 * Supabase anonymous auth gives every install a stable `auth.uid()` without an
 * account form. The uid doubles as `members.id`, which keeps RLS simple.
 *
 * The auth plugin persists the session in SharedPreferences, so a cold start
 * restores the same uid before the first network call.
 */
class AuthRepository(private val client: SupabaseClient?) {

    val isAvailable: Boolean get() = client != null

    suspend fun ensureUserId(): String? {
        val supabase = client ?: return null
        return runCatching {
            supabase.auth.awaitInitialization()
            supabase.auth.currentUserOrNull()?.id ?: run {
                supabase.auth.signInAnonymously()
                supabase.auth.currentUserOrNull()?.id
            }
        }.onFailure { Log.w(TAG, "Anonymous sign-in unavailable", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
