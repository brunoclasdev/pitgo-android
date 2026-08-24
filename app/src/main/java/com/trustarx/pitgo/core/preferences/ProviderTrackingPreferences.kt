package com.trustarx.pitgo.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.providerTrackingDataStore by preferencesDataStore(name = "provider_tracking")

data class ProviderTrackingLease(val accountId: String, val trackerId: String, val sequence: Long)

class ProviderTrackingPreferences(context: Context) {
    private val store = context.applicationContext.providerTrackingDataStore

    suspend fun lease(accountId: String): ProviderTrackingLease {
        val current = store.data.first()
        val existingAccount = current[AccountId]
        val tracker = current[TrackerId]
        if (existingAccount == accountId && tracker != null) {
            return ProviderTrackingLease(accountId, tracker, current[Sequence] ?: 0L)
        }
        val created = ProviderTrackingLease(accountId, UUID.randomUUID().toString(), 0L)
        store.edit { it[AccountId] = created.accountId; it[TrackerId] = created.trackerId; it[Sequence] = 0L }
        return created
    }

    suspend fun updateSequence(accountId: String, trackerId: String, sequence: Long) {
        store.edit {
            if (it[AccountId] == accountId && it[TrackerId] == trackerId) it[Sequence] = sequence
        }
    }

    suspend fun clear() = store.edit { it.clear() }

    private companion object {
        val AccountId = stringPreferencesKey("account_id")
        val TrackerId = stringPreferencesKey("tracker_id")
        val Sequence = longPreferencesKey("sequence")
    }
}
