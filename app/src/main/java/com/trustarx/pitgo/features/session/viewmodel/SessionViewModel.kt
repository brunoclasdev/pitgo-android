package com.trustarx.pitgo.features.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import kotlinx.coroutines.launch

class SessionViewModel(
    private val repository: SessionRepository,
) : ViewModel() {
    val state = repository.state

    init {
        viewModelScope.launch { repository.initialize() }
    }

    fun retry() {
        viewModelScope.launch { repository.retry() }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun logout() {
        viewModelScope.launch { repository.signOut() }
    }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SessionViewModel::class.java))
                    return SessionViewModel(repository) as T
                }
            }
    }
}
