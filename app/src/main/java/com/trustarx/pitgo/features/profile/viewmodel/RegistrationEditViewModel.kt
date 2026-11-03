package com.trustarx.pitgo.features.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.address.model.*
import com.trustarx.pitgo.core.address.repository.ViaCepAddressLookupRepository
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.common.validation.BrazilianInputValidation
import com.trustarx.pitgo.features.profile.model.*
import com.trustarx.pitgo.features.profile.repository.RegistrationEditRepository
import com.trustarx.pitgo.features.providerregistration.repository.AddressVerificationResult
import com.trustarx.pitgo.features.providerregistration.repository.ProviderAddressVerificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegistrationEditViewModel(
    private val ownerId: String,
    private val repository: RegistrationEditRepository,
    private val session: SessionRepository,
    private val postalCodes: ViaCepAddressLookupRepository,
    private val addressVerification: ProviderAddressVerificationRepository,
) : ViewModel() {
    private val mutable=MutableStateFlow<RegistrationEditState>(RegistrationEditState.Loading)
    val state=mutable.asStateFlow()
    private var operation: Job?=null
    init {
        reload()
        viewModelScope.launch {
            session.state.collect {
                if((it as? AppSessionState.Authenticated)?.userId != ownerId) {
                    operation?.cancel(); mutable.value=RegistrationEditState.SessionExpired
                }
            }
        }
    }
    fun reload() {
        if(operation?.isActive==true) return
        mutable.value=RegistrationEditState.Loading
        operation=viewModelScope.launch {
            when(val result=repository.load()) {
                is RegistrationResult.Success -> {
                    if(result.snapshot.ownerId==ownerId && isCurrentOwner()) mutable.value=RegistrationEditState.Content(result.snapshot)
                    else mutable.value=RegistrationEditState.SessionExpired
                }
                is RegistrationResult.Failure -> mutable.value=if(result.reason==RegistrationFailure.SESSION_EXPIRED) RegistrationEditState.SessionExpired else RegistrationEditState.Failed(result.reason)
            }
        }
    }
    private fun isCurrentOwner()=(session.state.value as? AppSessionState.Authenticated)?.userId==ownerId
    fun update(field: RegistrationField,value: String) {
        val current=mutable.value as? RegistrationEditState.Content ?: return
        if(current.busy) return
        val d=current.draft; val a=d.address
        val updated=when(field) {
            RegistrationField.NAME -> d.copy(name=value.take(150))
            RegistrationField.PHONE -> d.copy(phone=BrazilianInputValidation.normalizePhone(value))
            RegistrationField.DESCRIPTION -> d.copy(description=value.take(2000))
            RegistrationField.STREET -> d.copy(address=a.copy(street=value.take(160)))
            RegistrationField.NUMBER -> d.copy(address=a.copy(number=value.take(40)))
            RegistrationField.NEIGHBORHOOD -> d.copy(address=a.copy(neighborhood=value.take(100)))
            RegistrationField.POSTAL_CODE -> d.copy(address=a.copy(postalCode=BrazilianInputValidation.normalizePostalCode(value)))
            RegistrationField.CITY -> d.copy(address=a.copy(city=value.take(100)))
            RegistrationField.STATE -> d.copy(address=a.copy(state=value.uppercase().take(2)))
            RegistrationField.COMPLEMENT -> d.copy(address=a.copy(complement=value.take(160)))
        }
        mutable.value=current.copy(draft=updated,invalidFields=current.invalidFields-field,failure=null,operation=RegistrationOperation.IDLE)
    }
    fun lookupPostalCode() {
        val current=mutable.value as? RegistrationEditState.Content ?: return
        if(current.busy) return
        if(!BrazilianInputValidation.isValidPostalCode(current.draft.address.postalCode)) {
            mutable.value=current.copy(invalidFields=current.invalidFields+RegistrationField.POSTAL_CODE);return
        }
        mutable.value=current.copy(operation=RegistrationOperation.LOOKING_UP_ADDRESS,failure=null)
        operation=viewModelScope.launch {
            val found=postalCodes.lookup(current.draft.address.postalCode)
            if(!isCurrentOwner()) return@launch
            mutable.value=when(found) {
                is PostalCodeLookupResult.Found -> {
                    val a=current.draft.address
                    current.copy(draft=current.draft.copy(address=a.copy(street=found.address.street?.takeIf { it.isNotBlank() } ?: a.street,
                        neighborhood=found.address.neighborhood?.takeIf { it.isNotBlank() } ?: a.neighborhood,
                        city=found.address.city,state=found.address.state,
                        complement=a.complement.ifBlank { found.address.complement.orEmpty() })),
                        invalidFields=emptySet(),operation=RegistrationOperation.IDLE,failure=null)
                }
                else -> current.copy(operation=RegistrationOperation.IDLE,failure=RegistrationFailure.ADDRESS_UNAVAILABLE)
            }
        }
    }
    fun save() {
        val current=mutable.value as? RegistrationEditState.Content ?: return
        if(current.busy || !current.dirty || operation?.isActive==true) return
        val errors=mutableSetOf<RegistrationField>()
        if(current.draft.name.isBlank()) errors+=RegistrationField.NAME
        if(!BrazilianInputValidation.isValidPhone(current.draft.phone)) errors+=RegistrationField.PHONE
        val addressChanged=current.draft.address != (current.snapshot.address ?: AddressDraft())
        val a=current.draft.address
        if(addressChanged) {
            if(!BrazilianInputValidation.isValidPostalCode(a.postalCode)) errors+=RegistrationField.POSTAL_CODE
            if(a.street.isBlank()) errors+=RegistrationField.STREET
            if(a.number.isBlank()) errors+=RegistrationField.NUMBER
            if(a.neighborhood.isBlank()) errors+=RegistrationField.NEIGHBORHOOD
            if(a.city.isBlank()) errors+=RegistrationField.CITY
            if(a.state !in BrazilianInputValidation.states) errors+=RegistrationField.STATE
        }
        if(errors.isNotEmpty()) { mutable.value=current.copy(invalidFields=errors);return }
        mutable.value=current.copy(operation=RegistrationOperation.SAVING,failure=null,invalidFields=emptySet())
        operation=viewModelScope.launch {
            val token=if(addressChanged && current.snapshot.type==RegistrationAccountType.PROVIDER) {
                when(val proof=addressVerification.verify(a)) {
                    is AddressVerificationResult.Verified -> proof.state.token
                    is AddressVerificationResult.Failure -> {
                        if(isCurrentOwner()) mutable.value=when(proof.reason) {
                            com.trustarx.pitgo.features.providerregistration.model.ProviderFailure.SessionExpired -> RegistrationEditState.SessionExpired
                            com.trustarx.pitgo.features.providerregistration.model.ProviderFailure.Offline -> current.copy(failure=RegistrationFailure.OFFLINE)
                            com.trustarx.pitgo.features.providerregistration.model.ProviderFailure.Timeout -> current.copy(failure=RegistrationFailure.TIMEOUT)
                            else -> current.copy(failure=RegistrationFailure.ADDRESS_UNAVAILABLE)
                        }
                        return@launch
                    }
                    else -> {
                        if(isCurrentOwner()) mutable.value=current.copy(failure=RegistrationFailure.ADDRESS_UNAVAILABLE)
                        return@launch
                    }
                }
            } else null
            when(val result=repository.save(current.snapshot,current.draft,token)) {
                is RegistrationResult.Success -> {
                    if(!isCurrentOwner() || result.snapshot.ownerId!=ownerId) return@launch
                    val snapshot=result.snapshot
                    when(val account=session.state.value) {
                        is AppSessionState.Authenticated.Client -> session.applyClientProfile(account.account.profile.copy(name=snapshot.name,phone=snapshot.phone))
                        is AppSessionState.Authenticated.ProviderReady -> session.applyProviderSnapshot(account.account.copy(name=snapshot.name,phone=snapshot.phone))
                        else -> Unit
                    }
                    mutable.value=RegistrationEditState.Content(snapshot,operation=RegistrationOperation.SAVED)
                }
                is RegistrationResult.Failure -> if(isCurrentOwner()) {
                    mutable.value=if(result.reason==RegistrationFailure.SESSION_EXPIRED) RegistrationEditState.SessionExpired
                        else current.copy(failure=result.reason,operation=RegistrationOperation.IDLE)
                }
            }
        }
    }
    companion object {
        fun factory(ownerId:String,repository:RegistrationEditRepository,session:SessionRepository,
            postalCodes:ViaCepAddressLookupRepository,addressVerification:ProviderAddressVerificationRepository)=object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T =
                RegistrationEditViewModel(ownerId,repository,session,postalCodes,addressVerification) as T
        }
    }
}
