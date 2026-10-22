package com.trustarx.pitgo.core.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.github.jan.supabase.auth.auth
import com.trustarx.pitgo.core.account.repository.AccountRepository
import com.trustarx.pitgo.core.account.repository.AccountContextRepository
import com.trustarx.pitgo.core.auth.data.SupabaseAuthDataSource
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.network.SupabaseClientProvider
import com.trustarx.pitgo.core.network.ConnectivityObserver
import com.trustarx.pitgo.core.address.repository.ViaCepAddressLookupRepository
import com.trustarx.pitgo.core.location.data.AndroidLocationDataSource
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.preferences.AppearancePreferences
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
import com.trustarx.pitgo.features.providertracking.notification.ProviderIncomingRequestNotifier
import com.trustarx.pitgo.features.appointmenttracking.repository.AppointmentTrackingRepository
import com.trustarx.pitgo.features.providermap.repository.ServiceMapRepository
import com.trustarx.pitgo.features.signup.repository.SupabaseSignUpRepository
import com.trustarx.pitgo.features.clientaddress.repository.ClientAddressRepository
import com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository
import com.trustarx.pitgo.features.pittec.repository.PitTecRepository
import com.trustarx.pitgo.core.routing.repository.PitTecRouteRepository
import com.trustarx.pitgo.core.preferences.ProviderTrackingPreferences
import com.trustarx.pitgo.features.payment.repository.PaymentRepository
import com.trustarx.pitgo.features.paymentmethods.repository.SavedCardRepository

class PitGoAppContainer(context: Context) {
    private val supabase = SupabaseClientProvider.client

    val inspectionAlerts: com.trustarx.pitgo.features.guincho.repository.GuinchoInspectionAlertRepository by lazy { com.trustarx.pitgo.features.guincho.repository.GuinchoInspectionAlertRepository(supabase) { sessionRepository } }
    val connectivityObserver = ConnectivityObserver(context)
    val authDataSource = SupabaseAuthDataSource(supabase)
    val accountRepository = AccountRepository(supabase)
    val accountContextRepository = AccountContextRepository(supabase, accountRepository)
    val vehicleRepository = VehicleRepository(supabase)
    val providerRegistrationRepository = ProviderRegistrationRepository(supabase)
    val providerAddressVerificationRepository = ProviderAddressVerificationRepository(supabase)
    val viaCepAddressLookupRepository = ViaCepAddressLookupRepository()
    val serviceCatalogRepository = ServiceCatalogRepository(supabase)
    val providerSignUpRepository = ProviderSignUpRepository(supabase)
    val providerMigrationCoordinator = ProviderMigrationCoordinator()
    val providerMigrationRepository = ProviderMigrationRepository(supabase)
    val providerLocationRepository = ProviderLocationRepository(supabase)
    val providerTrackingStateRepository = ProviderTrackingStateRepository(context.applicationContext)
    val providerIncomingRequestNotifier = ProviderIncomingRequestNotifier(context.applicationContext)
    val providerTrackingPreferences = ProviderTrackingPreferences(context)
    val appointmentTrackingRepository = AppointmentTrackingRepository(supabase)
    val serviceMapRepository = ServiceMapRepository(supabase)
    val guinchoAddressRepository = com.trustarx.pitgo.features.guincho.repository.GuinchoAddressRepository(context, supabase)
    val guinchoInspectionFiles = com.trustarx.pitgo.features.guincho.data.GuinchoInspectionCaptureFiles(context)
    val guinchoInspectionRepository = com.trustarx.pitgo.features.guincho.repository.GuinchoInspectionRepository(supabase, guinchoInspectionFiles)
    val guinchoRouteRepository = com.trustarx.pitgo.features.guincho.repository.GuinchoRouteRepository(supabase, guinchoInspectionFiles)
    val guinchoRepository = com.trustarx.pitgo.features.guincho.repository.GuinchoRepository(supabase)
    val serviceRatingRepository = com.trustarx.pitgo.features.rating.repository.ServiceRatingRepository(supabase)
    val providerDashboardRepository = ProviderDashboardRepository(supabase)
    val mobileDispatchRepository = MobileDispatchRepository(supabase)
    val pitTecRepository = PitTecRepository(supabase)
    val pitTecRouteRepository = PitTecRouteRepository(supabase)
    val simulatedPitTecFlowRepository = com.trustarx.pitgo.features.pittec.repository.SimulatedPitTecFlowRepository()
    val paymentRepository: com.trustarx.pitgo.features.payment.repository.PaymentGateway =
        if (com.trustarx.pitgo.core.environment.EnvironmentConfig.simulated) com.trustarx.pitgo.features.payment.repository.SimulatedPaymentGateway(supabase)
        else com.trustarx.pitgo.features.payment.repository.IntegratedPaymentGateway(PaymentRepository(supabase))
    val savedCardRepository = SavedCardRepository(supabase)
    val clientAddressRepository = ClientAddressRepository(supabase)
    val deviceLocationRepository = DeviceLocationRepository(
        AndroidLocationDataSource(context), CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
    val onboardingRepository = VehicleOnboardingRepository(context)
    val appearancePreferences = AppearancePreferences(context)
    val sessionRepository: SessionRepository = SessionRepository(
        authDataSource,
        accountContextRepository,
        beforeInitialize = {
            supabase.auth.awaitInitialization()
            com.trustarx.pitgo.core.environment.EnvironmentStateRepository(context).validate {
                guinchoInspectionFiles.clear()
                guinchoInspectionRepository.pending.hide()
                guinchoInspectionFiles.clearReadCache()
                supabase.auth.sessionManager.deleteSession()
                context.stopService(android.content.Intent(context, com.trustarx.pitgo.features.providertracking.service.ProviderLocationTrackingService::class.java))
                providerTrackingPreferences.clear()
                onboardingRepository.clear()
                deviceLocationRepository.clearAllVisibleUses()
                androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
            }
            supabase.auth.loadFromStorage()
        },
        beforeSignOut = { provider ->
            guinchoInspectionFiles.clear()
            guinchoInspectionRepository.pending.hide()
            guinchoInspectionFiles.clearReadCache()
            if (provider != null) {
                providerDashboardRepository.update(
                    provider,
                    available = false,
                    mode = provider.locationMode ?: com.trustarx.pitgo.core.account.model.ProviderLocationMode.MOBILE,
                )
            }
            providerTrackingStateRepository.stop(updateAvailability = false)
            providerTrackingPreferences.clear()
            deviceLocationRepository.clearAllVisibleUses()
        },
    )
    val signUpRepository = SupabaseSignUpRepository(
        client = supabase,
        accountRepository = accountRepository,
        onboardingRepository = onboardingRepository,
    )
}
