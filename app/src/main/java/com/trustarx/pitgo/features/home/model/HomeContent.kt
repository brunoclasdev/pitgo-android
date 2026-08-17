package com.trustarx.pitgo.features.home.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trustarx.pitgo.R

data class DemoProfile(
    @param:StringRes val greetingRes: Int,
    @param:StringRes val nameRes: Int,
    @param:StringRes val avatarInitialRes: Int,
)

data class DiagnosticPrompt(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
)

val DemoHomeProfile = DemoProfile(
    greetingRes = R.string.home_greeting,
    nameRes = R.string.home_user_name,
    avatarInitialRes = R.string.home_avatar_initial,
)

val DemoDiagnosticPrompt = DiagnosticPrompt(
    titleRes = R.string.diagnostic_title,
    descriptionRes = R.string.diagnostic_description,
    iconRes = R.drawable.ic_home_diagnostic,
)
