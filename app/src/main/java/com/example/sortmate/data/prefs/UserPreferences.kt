package com.example.sortmate.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sortmate_session")

data class UserSession(
    val memberId: String? = null,
    val householdId: String? = null,
    val displayName: String? = null,
    val onboardingComplete: Boolean = false,
    val billReminders: Boolean = true,
    val choreReminders: Boolean = true,
    val quietHoursAlerts: Boolean = true,
    val activityAlerts: Boolean = true,
)

class UserPreferences(private val context: Context) {

    private object Keys {
        val MEMBER_ID = stringPreferencesKey("member_id")
        val HOUSEHOLD_ID = stringPreferencesKey("household_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val BILL_REMINDERS = booleanPreferencesKey("bill_reminders")
        val CHORE_REMINDERS = booleanPreferencesKey("chore_reminders")
        val QUIET_HOURS_ALERTS = booleanPreferencesKey("quiet_hours_alerts")
        val ACTIVITY_ALERTS = booleanPreferencesKey("activity_alerts")
    }

    val session: Flow<UserSession> = context.sessionDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            UserSession(
                memberId = preferences[Keys.MEMBER_ID],
                householdId = preferences[Keys.HOUSEHOLD_ID],
                displayName = preferences[Keys.DISPLAY_NAME],
                onboardingComplete = preferences[Keys.ONBOARDING_COMPLETE] ?: false,
                billReminders = preferences[Keys.BILL_REMINDERS] ?: true,
                choreReminders = preferences[Keys.CHORE_REMINDERS] ?: true,
                quietHoursAlerts = preferences[Keys.QUIET_HOURS_ALERTS] ?: true,
                activityAlerts = preferences[Keys.ACTIVITY_ALERTS] ?: true,
            )
        }

    suspend fun saveOnboarding(memberId: String, householdId: String, displayName: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[Keys.MEMBER_ID] = memberId
            preferences[Keys.HOUSEHOLD_ID] = householdId
            preferences[Keys.DISPLAY_NAME] = displayName
            preferences[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun setBillReminders(enabled: Boolean) = setBoolean(Keys.BILL_REMINDERS, enabled)

    suspend fun setChoreReminders(enabled: Boolean) = setBoolean(Keys.CHORE_REMINDERS, enabled)

    suspend fun setQuietHoursAlerts(enabled: Boolean) = setBoolean(Keys.QUIET_HOURS_ALERTS, enabled)

    suspend fun setActivityAlerts(enabled: Boolean) = setBoolean(Keys.ACTIVITY_ALERTS, enabled)

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, enabled: Boolean) {
        context.sessionDataStore.edit { it[key] = enabled }
    }
}
