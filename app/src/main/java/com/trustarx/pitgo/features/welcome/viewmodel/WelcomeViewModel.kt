package com.trustarx.pitgo.features.welcome.viewmodel

import androidx.lifecycle.ViewModel
import com.trustarx.pitgo.features.welcome.model.WelcomeBenefit
import com.trustarx.pitgo.features.welcome.model.WelcomeBenefits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WelcomeUiState(
    val benefits: List<WelcomeBenefit>,
)

class WelcomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        WelcomeUiState(
            benefits = WelcomeBenefits.sortedBy(WelcomeBenefit::displayOrder),
        ),
    )

    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()
}
