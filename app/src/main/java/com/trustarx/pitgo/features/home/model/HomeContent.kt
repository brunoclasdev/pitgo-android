package com.trustarx.pitgo.features.home.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.Profile
import com.trustarx.pitgo.core.common.model.DemoService
import com.trustarx.pitgo.core.vehicle.model.Vehicle

data class DiagnosticPrompt(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
)

val HomeDiagnosticPrompt = DiagnosticPrompt(
    titleRes = R.string.diagnostic_title,
    descriptionRes = R.string.diagnostic_description,
    iconRes = R.drawable.ic_home_diagnostic,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(
        val profile: Profile,
        val vehicle: Vehicle,
        val services: List<DemoService>,
    ) : HomeUiState
    data class NoVehicle(
        val profile: Profile,
        val services: List<DemoService>,
    ) : HomeUiState
    data object Failure : HomeUiState
}
