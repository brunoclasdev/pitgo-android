package com.trustarx.pitgo.features.store.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.store.model.*
import com.trustarx.pitgo.features.store.repository.StoreRepository
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StoreViewModel(private val repository: StoreRepository, private val ownerId: String?) : ViewModel() {
    private val mutable = MutableStateFlow(StoreUiState(selectedStoreId = ownerId))
    val state = mutable.asStateFlow()
    init { refresh() }

    fun openStore(store: StoreSummary) {
        if (mutable.value.operation != StoreOperation.IDLE) return
        mutable.update { it.copy(selectedStoreId = store.id, selectedStoreName = store.name, products = emptyList()) }
        refresh()
    }
    fun backToStores() {
        if (ownerId == null && mutable.value.operation == StoreOperation.IDLE) {
            mutable.update { it.copy(selectedStoreId = null, selectedStoreName = null, products = emptyList(), error = null) }
            refresh()
        }
    }
    fun refresh() = runOperation(StoreOperation.LOADING) {
        val id = mutable.value.selectedStoreId
        if (id == null) { val stores = repository.stores(); mutable.update { it.copy(stores = stores) } }
        else { val products = repository.products(id); mutable.update { it.copy(products = products) } }
    }
    fun edit(product: StoreProduct? = null) {
        if (ownerId == null || mutable.value.operation != StoreOperation.IDLE) return
        mutable.update { it.copy(error = null, draft = product?.let { p ->
            ProductDraft(p.id, p.name, p.description, p.amount.toPlainString(), p.imagePath, active = p.active)
        } ?: ProductDraft()) }
    }
    fun update(field: String, value: String) {
        if (mutable.value.operation != StoreOperation.IDLE) return
        mutable.update { current -> current.copy(error = null, draft = current.draft?.let { d -> when (field) {
            "name" -> d.copy(name = value); "description" -> d.copy(description = value)
            "price" -> d.copy(price = value); else -> d
        } }) }
    }
    fun photo(bytes: ByteArray, mime: String) {
        if (mutable.value.operation != StoreOperation.IDLE) return
        if (bytes.size > 5 * 1024 * 1024 || mime !in setOf("image/jpeg", "image/png", "image/webp")) {
            photoFailure(); return
        }
        mutable.update { it.copy(draft = it.draft?.copy(photo = bytes, photoMime = mime), error = null) }
    }
    fun photoFailure() { mutable.update { it.copy(error = "Escolha uma imagem JPG, PNG ou WebP de até 5 MB.") } }
    fun cancelEdit() { if (mutable.value.operation == StoreOperation.IDLE) mutable.update { it.copy(draft = null, error = null) } }
    fun save() {
        val draft = mutable.value.draft ?: return
        val amount = draft.price.replace(',', '.').toBigDecimalOrNull()
        if (draft.name.isBlank() || draft.name.length > 150 || draft.description.isBlank() || draft.description.length > 2000 ||
            amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 10) {
            mutable.update { it.copy(error = "Preencha nome, descrição e um preço válido maior que zero.") }; return
        }
        runOperation(StoreOperation.SAVING) {
            var updated = draft
            if (draft.photo != null) {
                val path = repository.upload(requireNotNull(ownerId), draft)
                updated = draft.copy(imagePath = path, photo = null)
                mutable.update { it.copy(draft = updated) }
            }
            repository.save(updated)
            val products = repository.products(requireNotNull(ownerId))
            mutable.update { it.copy(draft = null, products = products) }
        }
    }
    fun delete(product: StoreProduct) = runOperation(StoreOperation.SAVING) {
        check(ownerId != null); repository.delete(product)
        val products = repository.products(ownerId)
        mutable.update { it.copy(products = products) }
    }
    private fun runOperation(operation: StoreOperation, block: suspend () -> Unit) {
        if (mutable.value.operation != StoreOperation.IDLE) return
        mutable.update { it.copy(operation = operation, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.update { it.copy(error = when {
                error is RestException && error.statusCode in listOf(401,403) -> "Sua sessão não permite esta ação. Entre novamente."
                else -> "Não foi possível concluir. Verifique a conexão e tente novamente."
            }) } }
            finally { mutable.update { it.copy(operation = StoreOperation.IDLE) } }
        }
    }
    companion object {
        fun factory(repository: StoreRepository, ownerId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StoreViewModel(repository, ownerId) as T
        }
    }
}
