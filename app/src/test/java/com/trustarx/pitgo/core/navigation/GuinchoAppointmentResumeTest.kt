package com.trustarx.pitgo.core.navigation

import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import org.junit.Assert.*
import org.junit.Test

class GuinchoAppointmentResumeTest {
    private val activeStatuses = setOf(
        MobileAppointmentStatus.AWAITING_PAYMENT,
        MobileAppointmentStatus.ACCEPTED,
        MobileAppointmentStatus.IN_PROGRESS,
        MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION,
    )

    @Test fun resumesFromHomeEveryTimeForEveryActiveStatus() {
        for (status in activeStatuses) {
            repeat(3) {
                assertTrue(shouldResumeGuinchoAppointment(status, "active", PitGoRoutes.ProviderDashboard, null, false))
            }
        }
    }

    @Test fun leavesCurrentTrackingAndInspectionOpen() {
        for (status in activeStatuses) {
            for (route in listOf(PitGoRoutes.AppointmentTracking, PitGoRoutes.GuinchoInspection)) {
                assertFalse(shouldResumeGuinchoAppointment(status, "active", route, "active", false))
            }
        }
    }

    @Test fun terminalAndPendingAppointmentsDoNotResume() {
        for (status in MobileAppointmentStatus.entries.filterNot { it in activeStatuses }) {
            assertFalse(shouldResumeGuinchoAppointment(status, "active", PitGoRoutes.ProviderDashboard, null, false))
        }
    }

    @Test fun anotherAppointmentOrHistoricalScreenDoesNotHideActiveAppointment() {
        for (route in listOf(PitGoRoutes.AppointmentTracking, PitGoRoutes.GuinchoInspection)) {
            assertTrue(shouldResumeGuinchoAppointment(MobileAppointmentStatus.IN_PROGRESS, "active", route, "other", false))
            assertTrue(shouldResumeGuinchoAppointment(MobileAppointmentStatus.IN_PROGRESS, "active", route, "active", true))
        }
    }
}
