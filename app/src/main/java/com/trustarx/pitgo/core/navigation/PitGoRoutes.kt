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
    const val AppointmentHistoryArg = "history"
    const val AppointmentTracking = "appointment-tracking/{$AppointmentIdArg}?history={$AppointmentHistoryArg}"
    const val ServiceMapArg = "serviceId"
    const val ServiceMap = "service-map/{$ServiceMapArg}"
    const val PitTecServiceArg = "pittecServiceId"
    const val PaymentRequestIdArg = "requestId"
    const val PaymentAppointmentIdArg = "appointmentId"
    const val PaymentQuoteVersionArg = "quoteVersion"
    const val PaymentAmountArg = "quotedAmount"
    // (019) Sem requestId de pagamento prévio — a busca do PitTec sempre gera seu próprio id
    // internamente agora; o pagamento acontece depois do aceite, não antes da busca.
    const val SimulatedPitTec = "simulated-pittec/{$PaymentRequestIdArg}"
    fun simulatedPitTec(requestId: String) = "simulated-pittec/${Uri.encode(requestId)}"
    const val PitTec = "pittec/{$PitTecServiceArg}"
    const val MobileDispatchServiceArg = "mobileServiceId"
    const val MobileDispatchNew =
        "mobile-dispatch/new/{$MobileDispatchServiceArg}?$PaymentRequestIdArg={$PaymentRequestIdArg}"
    const val MobileDispatchSearchArg = "searchId"
    const val MobileDispatch = "mobile-dispatch/{$MobileDispatchSearchArg}"
    const val PaymentServiceArg = "paymentServiceId"
    const val PaymentDispatchKindArg = "paymentDispatchKind"
    // (019) `requestId` opcional: quando presente, o checkout cobra pelo id de busca já existente
    // (PitTec, pago só depois do aceite) em vez de gerar um novo id (caminho original, guincho
    // móvel, pago antes do despacho).
    const val Payment =
        "payment/{$PaymentServiceArg}/{$PaymentDispatchKindArg}?$PaymentRequestIdArg={$PaymentRequestIdArg}" +
            "&$PaymentAppointmentIdArg={$PaymentAppointmentIdArg}&$PaymentQuoteVersionArg={$PaymentQuoteVersionArg}&$PaymentAmountArg={$PaymentAmountArg}"
    const val ProviderRequest = "provider-request/{$AppointmentIdArg}"
    const val Home = "home"
    const val GuinchoMap = "guincho-map"
    const val GuinchoHistory = "guincho-history"
    const val ServiceRating = "service-rating/{$AppointmentIdArg}"
    const val ProviderRatings = "provider-ratings"
    fun serviceRating(appointmentId: String) = "service-rating/${Uri.encode(appointmentId)}"
    const val GuinchoInspection = "guincho-inspection/{$AppointmentIdArg}?history={$AppointmentHistoryArg}"
    fun guinchoInspection(appointmentId: String, historical: Boolean = false) = "guincho-inspection/${Uri.encode(appointmentId)}?history=$historical"
    const val VehicleOnboarding = "vehicle-onboarding"
    const val Garage = "garage"
    const val Profile = "profile"
    const val ClientAddress = "client-address"
    const val PaymentMethods = "payment-methods"
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
    fun appointmentTracking(appointmentId: String, historical: Boolean = false) = "appointment-tracking/${Uri.encode(appointmentId)}?history=$historical"
    fun serviceMap(serviceId: String) = "service-map/${Uri.encode(serviceId)}"

    fun pitTec(serviceId: String) = "pittec/${Uri.encode(serviceId)}"

    fun mobileDispatchNew(serviceId: String, requestId: String? = null): String {
        val base = "mobile-dispatch/new/${Uri.encode(serviceId)}"
        return if (requestId != null) "$base?$PaymentRequestIdArg=${Uri.encode(requestId)}" else base
    }

    fun mobileDispatch(searchId: String) = "mobile-dispatch/${Uri.encode(searchId)}"
    fun providerRequest(appointmentId: String) = "provider-request/${Uri.encode(appointmentId)}"
    const val PaymentHistory = "payment-history"

    fun resumePayment(payment: com.trustarx.pitgo.features.payment.model.Payment): String =
        payment(payment.serviceId ?: "legacy", payment.dispatchKind.name, payment.requestId,
            payment.appointmentId, payment.quoteVersion, payment.amount.toPlainString())

    fun payment(
        serviceId: String,
        dispatchKind: String,
        requestId: String? = null,
        appointmentId: String? = null,
        quoteVersion: Long? = null,
        quotedAmount: String? = null,
    ): String {
        val base = "payment/${Uri.encode(serviceId)}/${Uri.encode(dispatchKind)}"
        val args = listOfNotNull(
            requestId?.let { PaymentRequestIdArg to it },
            appointmentId?.let { PaymentAppointmentIdArg to it },
            quoteVersion?.let { PaymentQuoteVersionArg to it.toString() },
            quotedAmount?.let { PaymentAmountArg to it },
        )
        return if (args.isEmpty()) base else base + "?" + args.joinToString("&") { (key, value) ->
            "$key=${Uri.encode(value)}"
        }
    }
}
