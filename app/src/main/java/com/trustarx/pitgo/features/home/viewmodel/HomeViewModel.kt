package com.trustarx.pitgo.features.home.viewmodel

import androidx.lifecycle.ViewModel
import com.trustarx.pitgo.features.home.model.ServiceCatalog
import com.trustarx.pitgo.features.home.model.ServiceCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val categories: List<ServiceCategory>,
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(categories = ServiceCatalog.sortedBy(ServiceCategory::displayOrder)),
    )

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
