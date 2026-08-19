package com.trustarx.pitgo.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.vehicleOnboardingDataStore by preferencesDataStore(
    name = "vehicle_onboarding",
)

class VehicleOnboardingRepository(context: Context) {
    private val dataStore = context.applicationContext.vehicleOnboardingDataStore

    suspend fun markPending(userId: String) {
        require(userId.isNotBlank())
        dataStore.edit { preferences ->
            preferences[PendingUserIds] = preferences[PendingUserIds].orEmpty() + userId
        }
    }

    suspend fun isPending(userId: String): Boolean =
        userId in dataStore.data.first()[PendingUserIds].orEmpty()

    suspend fun resolve(userId: String) {
        dataStore.edit { preferences ->
            preferences[PendingUserIds] = preferences[PendingUserIds].orEmpty() - userId
        }
    }

    private companion object {
        val PendingUserIds = stringSetPreferencesKey("pending_user_ids")
    }
}
