package com.trustarx.pitgo.features.providermigration.repository

import com.trustarx.pitgo.features.providermigration.model.ProviderMigrationStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.Base64

class ProviderMigrationCoordinator {
    private val _stage = MutableStateFlow(ProviderMigrationStage.NONE)
    val stage: StateFlow<ProviderMigrationStage> = _stage.asStateFlow()
    private var token: CharArray? = null

    fun createProof(): String {
        clear()
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        token = value.toCharArray()
        return value
    }

    fun markAwaitingProvider() { _stage.value = ProviderMigrationStage.AWAITING_PROVIDER }
    fun markClaiming() { _stage.value = ProviderMigrationStage.CLAIMING }
    fun proofOrNull(): String? = token?.concatToString()
    fun clear() {
        token?.fill('\u0000')
        token = null
        _stage.value = ProviderMigrationStage.NONE
    }
}
