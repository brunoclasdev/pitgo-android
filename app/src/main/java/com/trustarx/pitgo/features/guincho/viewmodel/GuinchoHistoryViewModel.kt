package com.trustarx.pitgo.features.guincho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.guincho.model.GuinchoResult
import com.trustarx.pitgo.features.guincho.repository.GuinchoRepository
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GuinchoHistoryState(val rows: List<MobileDispatchSnapshot> = emptyList(),val loading: Boolean = false,
    val failed: Boolean = false,val hasMore: Boolean = false)
class GuinchoHistoryViewModel(private val repository: GuinchoRepository): ViewModel() {
    private val mutable=MutableStateFlow(GuinchoHistoryState())
    val state=mutable.asStateFlow()
    fun load(more: Boolean = false) {
        if(mutable.value.loading) return
        val old=mutable.value.rows
        val cursor=if(more) old.lastOrNull() else null
        mutable.value=mutable.value.copy(loading=true,failed=false)
        viewModelScope.launch {
            when(val result=repository.history(cursor?.createdAt,cursor?.appointment?.id)) {
                is GuinchoResult.Success -> mutable.value=GuinchoHistoryState(
                    rows=(if(more) old+result.value else result.value).distinctBy { it.appointment?.id },hasMore=result.value.size==20)
                is GuinchoResult.Failure -> mutable.value=mutable.value.copy(loading=false,failed=true)
            }
        }
    }
    companion object {
        fun factory(repository: GuinchoRepository) = object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = GuinchoHistoryViewModel(repository) as T
        }
    }
}
