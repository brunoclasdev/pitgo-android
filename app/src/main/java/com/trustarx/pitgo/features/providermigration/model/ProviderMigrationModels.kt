package com.trustarx.pitgo.features.providermigration.model

sealed interface ProviderMigrationState {
    data object Idle : ProviderMigrationState
    data object Starting : ProviderMigrationState
    data object SwitchAccount : ProviderMigrationState
    data object Claiming : ProviderMigrationState
    data object Completed : ProviderMigrationState
    data class Failure(val retryable: Boolean) : ProviderMigrationState
}

sealed interface ProviderMigrationResult {
    data object Pending : ProviderMigrationResult
    data object Migrated : ProviderMigrationResult
    data object SessionExpired : ProviderMigrationResult
    data object Unavailable : ProviderMigrationResult
    data object Failed : ProviderMigrationResult
}

enum class ProviderMigrationStage { NONE, AWAITING_PROVIDER, CLAIMING }
