package com.trustarx.pitgo.features.welcome.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trustarx.pitgo.R

enum class WelcomeBenefitId {
    FAST_SERVICE,
    VERIFIED_TECHNICIANS,
    ALL_IN_APP,
}

data class WelcomeBenefit(
    val id: WelcomeBenefitId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
    val displayOrder: Int,
)

val WelcomeBenefits = listOf(
    WelcomeBenefit(
        id = WelcomeBenefitId.FAST_SERVICE,
        titleRes = R.string.benefit_fast_title,
        descriptionRes = R.string.benefit_fast_description,
        iconRes = R.drawable.ic_benefit_fast_service,
        displayOrder = 0,
    ),
    WelcomeBenefit(
        id = WelcomeBenefitId.VERIFIED_TECHNICIANS,
        titleRes = R.string.benefit_verified_title,
        descriptionRes = R.string.benefit_verified_description,
        iconRes = R.drawable.ic_benefit_verified,
        displayOrder = 1,
    ),
    WelcomeBenefit(
        id = WelcomeBenefitId.ALL_IN_APP,
        titleRes = R.string.benefit_app_title,
        descriptionRes = R.string.benefit_app_description,
        iconRes = R.drawable.ic_benefit_all_in_app,
        displayOrder = 2,
    ),
)
