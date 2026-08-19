package com.trustarx.pitgo.core.app

import android.content.Context
import com.trustarx.pitgo.core.account.repository.AccountRepository
import com.trustarx.pitgo.core.auth.data.SupabaseAuthDataSource
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.network.SupabaseClientProvider
import com.trustarx.pitgo.core.preferences.VehicleOnboardingRepository
import com.trustarx.pitgo.core.vehicle.repository.VehicleRepository
import com.trustarx.pitgo.features.signup.repository.SupabaseSignUpRepository

class PitGoAppContainer(context: Context) {
    private val supabase = SupabaseClientProvider.client

    val authDataSource = SupabaseAuthDataSource(supabase)
    val accountRepository = AccountRepository(supabase)
    val vehicleRepository = VehicleRepository(supabase)
    val onboardingRepository = VehicleOnboardingRepository(context)
    val sessionRepository = SessionRepository(authDataSource, accountRepository)
    val signUpRepository = SupabaseSignUpRepository(
        client = supabase,
        accountRepository = accountRepository,
        onboardingRepository = onboardingRepository,
    )
}
