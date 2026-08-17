package com.trustarx.pitgo.features.home.viewmodel

import androidx.lifecycle.ViewModel
import com.trustarx.pitgo.features.home.model.BottomItemId
import com.trustarx.pitgo.features.home.model.BottomNavigationItem
import com.trustarx.pitgo.features.home.model.DemoBottomNavigation
import com.trustarx.pitgo.features.home.model.DemoDiagnosticPrompt
import com.trustarx.pitgo.features.home.model.DemoHomeProfile
import com.trustarx.pitgo.features.home.model.DemoHomeVehicle
import com.trustarx.pitgo.features.home.model.DemoProfile
import com.trustarx.pitgo.features.home.model.DemoService
import com.trustarx.pitgo.features.home.model.DemoServices
import com.trustarx.pitgo.features.home.model.DemoVehicle
import com.trustarx.pitgo.features.home.model.DiagnosticPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val profile: DemoProfile,
    val vehicle: DemoVehicle,
    val diagnosticPrompt: DiagnosticPrompt,
    val services: List<DemoService>,
    val bottomNavigationItems: List<BottomNavigationItem>,
    val selectedBottomItem: BottomItemId,
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            profile = DemoHomeProfile,
            vehicle = DemoHomeVehicle,
            diagnosticPrompt = DemoDiagnosticPrompt,
            services = DemoServices.sortedBy(DemoService::displayOrder),
            bottomNavigationItems = DemoBottomNavigation.sortedBy(BottomNavigationItem::displayOrder),
            selectedBottomItem = BottomItemId.HOME,
        ),
    )

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
