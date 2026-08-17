package com.trustarx.pitgo.core.common.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trustarx.pitgo.R

enum class DemoServiceId {
    BATTERY,
    OIL_CHANGE,
    TIRES,
    DASHBOARD_LIGHT,
    SMALL_REPAIRS,
    CAR_WONT_START,
}

data class DemoService(
    val id: DemoServiceId,
    @param:StringRes val nameRes: Int,
    val startingPrice: Int,
    @param:DrawableRes val iconRes: Int,
    val displayOrder: Int,
)

val DemoServices = listOf(
    DemoService(DemoServiceId.BATTERY, R.string.service_battery, 320, R.drawable.ic_service_battery, 0),
    DemoService(DemoServiceId.OIL_CHANGE, R.string.service_oil_change, 220, R.drawable.ic_service_oil_change, 1),
    DemoService(DemoServiceId.TIRES, R.string.service_tires, 180, R.drawable.ic_service_tire_change, 2),
    DemoService(DemoServiceId.DASHBOARD_LIGHT, R.string.service_dashboard_light, 150, R.drawable.ic_service_dashboard_light, 3),
    DemoService(DemoServiceId.SMALL_REPAIRS, R.string.service_small_repairs, 260, R.drawable.ic_service_mechanic, 4),
    DemoService(DemoServiceId.CAR_WONT_START, R.string.service_car_wont_start, 280, R.drawable.ic_service_car_wont_start, 5),
)
