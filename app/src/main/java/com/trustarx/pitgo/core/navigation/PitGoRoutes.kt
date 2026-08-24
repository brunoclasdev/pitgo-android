package com.trustarx.pitgo.core.navigation

import android.net.Uri

object PitGoRoutes {
    const val SessionGate = "session-gate"
    const val Welcome = "welcome"
    const val Login = "login"
    const val SignUp = "sign-up"
    const val SignUpPattern = SignUp
    const val ProviderSignUp = "provider-sign-up"
    const val ProviderDashboard = "provider-dashboard"
    const val ProviderMigration = "provider-migration"
    const val AppointmentIdArg = "appointmentId"
    const val ProviderTracking = "provider-tracking/{$AppointmentIdArg}"
    const val AppointmentTracking = "appointment-tracking/{$AppointmentIdArg}"
    const val ServiceMapArg = "serviceId"
    const val ServiceMap = "service-map/{$ServiceMapArg}"
    const val MobileDispatchServiceArg = "mobileServiceId"
    const val MobileDispatchNew = "mobile-dispatch/new/{$MobileDispatchServiceArg}"
    const val MobileDispatchSearchArg = "searchId"
    const val MobileDispatch = "mobile-dispatch/{$MobileDispatchSearchArg}"
    const val ProviderRequest = "provider-request/{$AppointmentIdArg}"
    const val Home = "home"
    const val VehicleOnboarding = "vehicle-onboarding"
    const val Garage = "garage"
    const val Profile = "profile"
    const val ClientAddress = "client-address"
    const val ProviderRegistration = "provider-registration"
    const val VehicleForm = "vehicle/new"
    const val VehicleFormOriginArg = "origin"
    const val VehicleFormPattern = "$VehicleForm?$VehicleFormOriginArg={$VehicleFormOriginArg}"
    const val RequestService = "request-service"
    const val RequestServiceArg = "serviceId"
    const val RequestProviderArg = "providerId"
    const val RequestServicePattern =
        "$RequestService?$RequestServiceArg={$RequestServiceArg}&$RequestProviderArg={$RequestProviderArg}"

    fun requestService(serviceId: String, providerId: String): String =
        "$RequestService?$RequestServiceArg=${Uri.encode(serviceId)}" +
            "&$RequestProviderArg=${Uri.encode(providerId)}"

    fun newVehicle(origin: String): String =
        "$VehicleForm?$VehicleFormOriginArg=${Uri.encode(origin)}"

    fun providerTracking(appointmentId: String) = "provider-tracking/${Uri.encode(appointmentId)}"
    fun appointmentTracking(appointmentId: String) = "appointment-tracking/${Uri.encode(appointmentId)}"
    fun serviceMap(serviceId: String) = "service-map/${Uri.encode(serviceId)}"
    fun mobileDispatchNew(serviceId: String) = "mobile-dispatch/new/${Uri.encode(serviceId)}"
    fun mobileDispatch(searchId: String) = "mobile-dispatch/${Uri.encode(searchId)}"
    fun providerRequest(appointmentId: String) = "provider-request/${Uri.encode(appointmentId)}"
}
