package com.trustarx.pitgo.core.environment

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.environmentStore by preferencesDataStore(name = "environment")

class EnvironmentStateRepository(context: Context) {
    private val store = context.applicationContext.environmentStore
    private val mutex = Mutex()
    private val fingerprint = stringPreferencesKey("fingerprint")
    suspend fun validate(clearIncompatible: suspend () -> Unit) = mutex.withLock {
        if (store.data.first()[fingerprint] != EnvironmentConfig.fingerprint) {
            clearIncompatible()
            store.edit { it[fingerprint] = EnvironmentConfig.fingerprint }
        }
    }
}
