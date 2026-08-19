package com.trustarx.pitgo.core.navigation

import android.net.Uri
import com.trustarx.pitgo.core.common.model.DemoServiceId

object PitGoRoutes {
    const val SessionGate = "session-gate"
    const val Welcome = "welcome"
    const val Login = "login"
    const val SignUp = "sign-up"
    const val Home = "home"
    const val VehicleOnboarding = "vehicle-onboarding"
    const val Garage = "garage"
    const val Profile = "profile"
    const val VehicleForm = "vehicle/new"
    const val VehicleFormOriginArg = "origin"
    const val VehicleFormPattern = "$VehicleForm?$VehicleFormOriginArg={$VehicleFormOriginArg}"
    const val RequestService = "request-service"
    const val RequestServiceArg = "serviceId"
    const val RequestServicePattern = "$RequestService?$RequestServiceArg={$RequestServiceArg}"

    fun requestService(serviceId: DemoServiceId): String =
        "$RequestService?$RequestServiceArg=${Uri.encode(serviceId.name)}"

    fun newVehicle(origin: String): String =
        "$VehicleForm?$VehicleFormOriginArg=${Uri.encode(origin)}"
}
