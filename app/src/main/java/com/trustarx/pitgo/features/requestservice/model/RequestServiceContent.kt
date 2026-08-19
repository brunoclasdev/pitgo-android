package com.trustarx.pitgo.features.requestservice.model

import androidx.annotation.StringRes
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.common.model.DemoService
import com.trustarx.pitgo.core.vehicle.model.Vehicle

enum class PaymentMethod(
    @param:StringRes val labelRes: Int,
) {
    PIX(R.string.request_payment_pix),
    CREDIT(R.string.request_payment_credit),
    DEBIT(R.string.request_payment_debit),
}

enum class SubmissionStatus {
    READY,
    TRIGGERED,
}

sealed interface RequestServiceUiState {
    data class Content(
        val selectedService: DemoService,
        val vehicle: Vehicle? = null,
        val problemDescription: String = "",
        @param:StringRes val estimatedArrivalRes: Int = R.string.request_arrival_value,
        val paymentMethod: PaymentMethod = PaymentMethod.PIX,
        val submissionStatus: SubmissionStatus = SubmissionStatus.READY,
    ) : RequestServiceUiState {
        val estimatedPrice: Int
            get() = selectedService.startingPrice
    }

    data object InvalidService : RequestServiceUiState
}
