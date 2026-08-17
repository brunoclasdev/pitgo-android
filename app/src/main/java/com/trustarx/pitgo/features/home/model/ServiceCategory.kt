package com.trustarx.pitgo.features.home.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trustarx.pitgo.R

enum class ServiceCategoryId(val routeValue: String) {
    TIRE_SHOP("tire-shop"),
    VEHICLE_ELECTRICIAN("vehicle-electrician"),
    BATTERY("battery"),
    TIRE_CHANGE("tire-change"),
    MECHANIC("mechanic");

    companion object {
        fun fromRouteValue(value: String?): ServiceCategoryId? =
            entries.firstOrNull { it.routeValue == value }
    }
}

data class ServiceCategory(
    val id: ServiceCategoryId,
    @param:StringRes val nameRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val accessibilityActionRes: Int,
    val displayOrder: Int,
)

val ServiceCatalog = listOf(
    ServiceCategory(
        id = ServiceCategoryId.TIRE_SHOP,
        nameRes = R.string.service_tire_shop,
        iconRes = R.drawable.ic_service_tire_shop,
        accessibilityActionRes = R.string.service_action_tire_shop,
        displayOrder = 0,
    ),
    ServiceCategory(
        id = ServiceCategoryId.VEHICLE_ELECTRICIAN,
        nameRes = R.string.service_vehicle_electrician,
        iconRes = R.drawable.ic_service_vehicle_electrician,
        accessibilityActionRes = R.string.service_action_vehicle_electrician,
        displayOrder = 1,
    ),
    ServiceCategory(
        id = ServiceCategoryId.BATTERY,
        nameRes = R.string.service_battery,
        iconRes = R.drawable.ic_service_battery,
        accessibilityActionRes = R.string.service_action_battery,
        displayOrder = 2,
    ),
    ServiceCategory(
        id = ServiceCategoryId.TIRE_CHANGE,
        nameRes = R.string.service_tire_change,
        iconRes = R.drawable.ic_service_tire_change,
        accessibilityActionRes = R.string.service_action_tire_change,
        displayOrder = 3,
    ),
    ServiceCategory(
        id = ServiceCategoryId.MECHANIC,
        nameRes = R.string.service_mechanic,
        iconRes = R.drawable.ic_service_mechanic,
        accessibilityActionRes = R.string.service_action_mechanic,
        displayOrder = 4,
    ),
)
