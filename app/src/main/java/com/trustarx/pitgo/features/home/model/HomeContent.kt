package com.trustarx.pitgo.features.home.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trustarx.pitgo.R

data class DemoProfile(
    @param:StringRes val greetingRes: Int,
    @param:StringRes val nameRes: Int,
    @param:StringRes val avatarInitialRes: Int,
)

data class DemoVehicle(
    @param:StringRes val modelRes: Int,
    val year: Int,
    @param:StringRes val plateRes: Int,
    @param:StringRes val addressRes: Int,
    @param:DrawableRes val iconRes: Int,
)

data class DiagnosticPrompt(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
)

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

enum class BottomItemId {
    HOME,
    SERVICES,
    GARAGE,
    APPOINTMENTS,
    PROFILE,
}

data class BottomNavigationItem(
    val id: BottomItemId,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val displayOrder: Int,
)

val DemoHomeProfile = DemoProfile(
    greetingRes = R.string.home_greeting,
    nameRes = R.string.home_user_name,
    avatarInitialRes = R.string.home_avatar_initial,
)

val DemoHomeVehicle = DemoVehicle(
    modelRes = R.string.home_vehicle_model,
    year = 2020,
    plateRes = R.string.home_vehicle_plate,
    addressRes = R.string.home_address,
    iconRes = R.drawable.ic_home_vehicle,
)

val DemoDiagnosticPrompt = DiagnosticPrompt(
    titleRes = R.string.diagnostic_title,
    descriptionRes = R.string.diagnostic_description,
    iconRes = R.drawable.ic_home_diagnostic,
)

val DemoServices = listOf(
    DemoService(DemoServiceId.BATTERY, R.string.service_battery, 320, R.drawable.ic_service_battery, 0),
    DemoService(DemoServiceId.OIL_CHANGE, R.string.service_oil_change, 220, R.drawable.ic_service_oil_change, 1),
    DemoService(DemoServiceId.TIRES, R.string.service_tires, 180, R.drawable.ic_service_tire_change, 2),
    DemoService(DemoServiceId.DASHBOARD_LIGHT, R.string.service_dashboard_light, 150, R.drawable.ic_service_dashboard_light, 3),
    DemoService(DemoServiceId.SMALL_REPAIRS, R.string.service_small_repairs, 260, R.drawable.ic_service_mechanic, 4),
    DemoService(DemoServiceId.CAR_WONT_START, R.string.service_car_wont_start, 280, R.drawable.ic_service_car_wont_start, 5),
)

val DemoBottomNavigation = listOf(
    BottomNavigationItem(BottomItemId.HOME, R.string.nav_home, R.drawable.ic_nav_home, 0),
    BottomNavigationItem(BottomItemId.SERVICES, R.string.nav_services, R.drawable.ic_nav_services, 1),
    BottomNavigationItem(BottomItemId.GARAGE, R.string.nav_garage, R.drawable.ic_nav_garage, 2),
    BottomNavigationItem(BottomItemId.APPOINTMENTS, R.string.nav_appointments, R.drawable.ic_nav_appointments, 3),
    BottomNavigationItem(BottomItemId.PROFILE, R.string.nav_profile, R.drawable.ic_nav_profile, 4),
)
