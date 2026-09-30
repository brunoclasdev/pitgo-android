package com.trustarx.pitgo.features.payment.repository

class IntegratedPaymentGateway(repository: PaymentRepository) : PaymentGateway by repository
