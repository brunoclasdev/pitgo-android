package com.trustarx.pitgo.core.common.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trustarx.pitgo.R

data class DemoVehicle(
    @param:StringRes val modelRes: Int,
    val year: Int,
    @param:StringRes val plateRes: Int,
    @param:StringRes val addressRes: Int,
    @param:DrawableRes val iconRes: Int,
)

val DemoHomeVehicle = DemoVehicle(
    modelRes = R.string.home_vehicle_model,
    year = 2020,
    plateRes = R.string.home_vehicle_plate,
    addressRes = R.string.home_address,
    iconRes = R.drawable.ic_home_vehicle,
)
