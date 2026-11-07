package com.trustarx.pitgo.core.navigation

import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus

internal fun shouldResumeGuinchoAppointment(
    status: MobileAppointmentStatus,
    appointmentId: String,
    currentRoute: String?,
    currentAppointmentId: String?,
    historical: Boolean,
): Boolean {
    val active = status in setOf(
        MobileAppointmentStatus.AWAITING_PAYMENT,
        MobileAppointmentStatus.ACCEPTED,
        MobileAppointmentStatus.IN_PROGRESS,
        MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION,
    )
    val showingActiveAppointment = !historical && currentAppointmentId == appointmentId &&
        currentRoute in setOf(PitGoRoutes.AppointmentTracking, PitGoRoutes.GuinchoInspection)
    return active && !showingActiveAppointment
}
