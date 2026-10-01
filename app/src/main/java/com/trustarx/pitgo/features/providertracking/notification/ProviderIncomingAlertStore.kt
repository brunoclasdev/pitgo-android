package com.trustarx.pitgo.features.providertracking.notification

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.incomingAlerts by preferencesDataStore("incoming_alert_delivery")

/** Only account/appointment IDs and expiry; no operational or personal payload. */
class ProviderIncomingAlertStore(context: Context) {
    private val store = context.applicationContext.incomingAlerts
    suspend fun post(providerId: String, appointmentId: String, deadline: Long, post: (Boolean) -> Boolean) = lock.withLock {
        val now = System.currentTimeMillis()
        if (deadline <= now) return@withLock
        val values = store.data.first()
        val expired = values.asMap().filterValues { it is Long && it <= now }.keys
        if (expired.isNotEmpty()) store.edit { prefs -> expired.forEach { prefs.remove(longPreferencesKey(it.name)) } }
        val key = longPreferencesKey("$providerId:$appointmentId")
        val silent = (values[key] ?: 0L) > now
        if (post(silent) && !silent) store.edit { it[key] = deadline }
    }
    suspend fun purgeExpired() = lock.withLock {
        val expired = store.data.first().asMap().filterValues { it is Long && it <= System.currentTimeMillis() }.keys
        if (expired.isNotEmpty()) store.edit { values -> expired.forEach { values.remove(longPreferencesKey(it.name)) } }
    }
    suspend fun finish(providerId: String, appointmentId: String) {
        lock.withLock { store.edit { it.remove(longPreferencesKey("$providerId:$appointmentId")) } }
    }
    companion object { private val lock = Mutex() }
}
