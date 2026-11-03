package com.trustarx.pitgo.features.profile.model

import com.trustarx.pitgo.core.account.ProviderKind
import com.trustarx.pitgo.core.address.model.AddressDraft

enum class RegistrationAccountType { CLIENT, PROVIDER }
enum class RegistrationField { NAME, PHONE, DESCRIPTION, POSTAL_CODE, STREET, NUMBER, NEIGHBORHOOD, CITY, STATE, COMPLEMENT }
enum class RegistrationOperation { IDLE, LOOKING_UP_ADDRESS, SAVING, SAVED }
enum class RegistrationFailure { OFFLINE, TIMEOUT, SESSION_EXPIRED, UNAVAILABLE, INVALID_INPUT, CONFLICT, ADDRESS_UNAVAILABLE, UNKNOWN }
data class RegistrationSnapshot(
    val ownerId: String, val type: RegistrationAccountType, val name: String, val phone: String,
    val email: String, val document: String, val description: String, val providerKind: ProviderKind?,
    val updatedAt: String, val address: AddressDraft?, val addressUpdatedAt: String?,
)
data class RegistrationDraft(val name: String, val phone: String, val description: String, val address: AddressDraft)
fun RegistrationSnapshot.draft() = RegistrationDraft(name, phone, description, address ?: AddressDraft())
sealed interface RegistrationEditState {
    data object Loading : RegistrationEditState
    data object SessionExpired : RegistrationEditState
    data class Failed(val reason: RegistrationFailure) : RegistrationEditState
    data class Content(
        val snapshot: RegistrationSnapshot, val draft: RegistrationDraft = snapshot.draft(),
        val operation: RegistrationOperation = RegistrationOperation.IDLE,
        val invalidFields: Set<RegistrationField> = emptySet(), val failure: RegistrationFailure? = null,
    ) : RegistrationEditState {
        val busy get() = operation in setOf(RegistrationOperation.LOOKING_UP_ADDRESS, RegistrationOperation.SAVING)
        val dirty get() = draft != snapshot.draft()
    }
}
sealed interface RegistrationResult {
    data class Success(val snapshot: RegistrationSnapshot) : RegistrationResult
    data class Failure(val reason: RegistrationFailure) : RegistrationResult
}
