package com.trustarx.pitgo.core.app

import android.content.Context
import com.trustarx.pitgo.core.account.repository.AccountRepository
import com.trustarx.pitgo.core.account.repository.AccountContextRepository
import com.trustarx.pitgo.core.auth.data.SupabaseAuthDataSource
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.network.SupabaseClientProvider
import com.trustarx.pitgo.core.network.ConnectivityObserver
import com.trustarx.pitgo.core.location.data.FusedLocationDataSource
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.preferences.VehicleOnboardingRepository
import com.trustarx.pitgo.core.vehicle.repository.VehicleRepository
import com.trustarx.pitgo.features.providerregistration.repository.ProviderRegistrationRepository
import com.trustarx.pitgo.features.providerregistration.repository.ProviderAddressVerificationRepository
import com.trustarx.pitgo.core.catalog.repository.ServiceCatalogRepository
import com.trustarx.pitgo.features.providerdashboard.repository.ProviderDashboardRepository
import com.trustarx.pitgo.features.providersignup.repository.ProviderSignUpRepository
import com.trustarx.pitgo.features.providermigration.repository.ProviderMigrationCoordinator
import com.trustarx.pitgo.features.providermigration.repository.ProviderMigrationRepository
import com.trustarx.pitgo.features.providertracking.repository.ProviderLocationRepository
import com.trustarx.pitgo.features.providertracking.repository.ProviderTrackingStateRepository
import com.trustarx.pitgo.features.appointmenttracking.repository.AppointmentTrackingRepository
import com.trustarx.pitgo.features.providermap.repository.ServiceMapRepository
import com.trustarx.pitgo.features.signup.repository.SupabaseSignUpRepository

class PitGoAppContainer(context: Context) {
    private val supabase = SupabaseClientProvider.client

    val connectivityObserver = ConnectivityObserver(context)
    val authDataSource = SupabaseAuthDataSource(supabase)
    val accountRepository = AccountRepository(supabase)
    val accountContextRepository = AccountContextRepository(supabase, accountRepository)
    val vehicleRepository = VehicleRepository(supabase)
    val providerRegistrationRepository = ProviderRegistrationRepository(supabase)
    val providerAddressVerificationRepository = ProviderAddressVerificationRepository(supabase)
    val serviceCatalogRepository = ServiceCatalogRepository(supabase)
    val providerSignUpRepository = ProviderSignUpRepository(supabase)
    val providerMigrationCoordinator = ProviderMigrationCoordinator()
    val providerMigrationRepository = ProviderMigrationRepository(supabase)
    val providerLocationRepository = ProviderLocationRepository(supabase)
    val providerTrackingStateRepository = ProviderTrackingStateRepository(context.applicationContext)
    val appointmentTrackingRepository = AppointmentTrackingRepository(supabase)
    val serviceMapRepository = ServiceMapRepository(supabase)
    val providerDashboardRepository = ProviderDashboardRepository(supabase)
    val deviceLocationRepository = DeviceLocationRepository(FusedLocationDataSource(context))
    val onboardingRepository = VehicleOnboardingRepository(context)
    val sessionRepository = SessionRepository(
        authDataSource,
        accountContextRepository,
        beforeSignOut = {
            providerTrackingStateRepository.stop()
            deviceLocationRepository.clearAllVisibleUses()
        },
    )
    val signUpRepository = SupabaseSignUpRepository(
        client = supabase,
        accountRepository = accountRepository,
        onboardingRepository = onboardingRepository,
    )
}
