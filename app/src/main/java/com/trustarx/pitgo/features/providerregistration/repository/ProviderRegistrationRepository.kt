package com.trustarx.pitgo.features.providerregistration.repository

import com.trustarx.pitgo.core.catalog.model.ServiceDispatchKind
import com.trustarx.pitgo.features.providerregistration.data.CatalogServiceDto
import com.trustarx.pitgo.features.providerregistration.data.ProviderAddressDto
import com.trustarx.pitgo.features.providerregistration.data.ProviderDto
import com.trustarx.pitgo.features.providerregistration.data.ProviderOfferDto
import com.trustarx.pitgo.features.providerregistration.data.ProviderRegistrationRpcParams
import com.trustarx.pitgo.features.providerregistration.data.ProviderRegistrationRpcResultDto
import com.trustarx.pitgo.features.providerregistration.data.ServiceCategoryDto
import com.trustarx.pitgo.features.providerregistration.data.decimalPrice
import com.trustarx.pitgo.features.providerregistration.data.toDomain
import com.trustarx.pitgo.features.providerregistration.data.toDto
import com.trustarx.pitgo.features.providerregistration.model.AddressSummary
import com.trustarx.pitgo.features.providerregistration.model.CatalogService
import com.trustarx.pitgo.features.providerregistration.model.ImplicitServiceResult
import com.trustarx.pitgo.features.providerregistration.model.OfferSummary
import com.trustarx.pitgo.features.providerregistration.model.ProviderCatalogResult
import com.trustarx.pitgo.features.providerregistration.model.ProviderEntryResult
import com.trustarx.pitgo.features.providerregistration.model.ProviderFailure
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistration
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistrationResult
import com.trustarx.pitgo.features.providerregistration.model.ProviderSummary
import com.trustarx.pitgo.features.providerregistration.model.ServiceCategory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Locale

class ProviderRegistrationRepository(private val client: SupabaseClient) {
    suspend fun loadEntry(userId: String): ProviderEntryResult = try {
        val provider = loadProvider(userId)
        if (provider != null) {
            ProviderEntryResult.AlreadyRegistered(loadSummary(provider))
        } else {
            ProviderEntryResult.Ready(loadAllCategories(activeOnly = true))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        ProviderEntryResult.Failure(classifyFailure(error))
    }

    suspend fun loadServices(categoryId: String): ProviderCatalogResult<CatalogService> = try {
        ProviderCatalogResult.Success(loadAllServices(categoryId, selectableOnly = true))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        ProviderCatalogResult.Failure(classifyFailure(error))
    }

    /**
     * Localiza o serviço implícito de Móvel/PitTec pelo `dispatch_kind` (nunca pelo nome — ver
     * research.md #1). Busca no máximo 2 registros ativos para distinguir ausência de
     * duplicidade sem exigir uma constraint de unicidade nova no banco (research.md #2).
     */
    suspend fun resolveImplicitService(dispatchKind: ServiceDispatchKind): ImplicitServiceResult = try {
        val matches = client.from(SERVICES).select {
            filter {
                eq("dispatch_kind", dispatchKind.name)
                eq("active", true)
            }
            limit(2)
        }.decodeList<CatalogServiceDto>().map(CatalogServiceDto::toDomain)
        when (matches.size) {
            1 -> ImplicitServiceResult.Found(matches.single())
            0 -> ImplicitServiceResult.Absent
            else -> ImplicitServiceResult.Duplicated
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        ImplicitServiceResult.Failure(classifyFailure(error))
    }

    suspend fun submit(
        userId: String,
        registration: ProviderRegistration,
    ): ProviderRegistrationResult = try {
        val params = ProviderRegistrationRpcParams(
            provider = registration.company.toDto(registration.locationMode.name),
            address = registration.address.toDto(),
            offers = registration.offers.map { it.toDto() },
        )
        val rpcResult = client.postgrest.rpc(
            CREATE_PROVIDER_REGISTRATION,
            Json.encodeToJsonElement(params).jsonObject,
        ).decodeAs<ProviderRegistrationRpcResultDto>()
        client.postgrest.rpc(
            SET_PROVIDER_AVAILABILITY,
            buildJsonObject { put("p_available", true) },
        )
        val summary = loadSummary(requireNotNull(loadProvider(userId)))
        if (rpcResult.outcome == OUTCOME_ALREADY_REGISTERED) {
            ProviderRegistrationResult.AlreadyRegistered(summary)
        } else {
            ProviderRegistrationResult.Success(summary)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        classifyRegistrationFailure(error)
    }

    private suspend fun loadProvider(userId: String): ProviderDto? = client.from(PROVIDERS).select {
        filter { eq("account_id", userId) }
        limit(1)
    }.decodeSingleOrNull()

    private suspend fun loadSummary(provider: ProviderDto): ProviderSummary {
        val providerId = provider.id
        val address = client.from(ADDRESSES).select {
            filter { eq("provider_id", providerId) }
            limit(1)
        }.decodeSingleOrNull<ProviderAddressDto>()
            ?: throw ProviderContractException
        val providerOffers = loadAllProviderOffers(providerId)
        if (providerOffers.isEmpty()) throw ProviderContractException
        val services = loadAllServices(categoryId = null).associateBy(CatalogService::id)
        val categories = loadAllCategories().associateBy(ServiceCategory::id)
        val offers = providerOffers.map { offer ->
            val service = services[offer.serviceId] ?: throw ProviderContractException
            val category = categories[service.categoryId] ?: throw ProviderContractException
            OfferSummary(
                categoryName = category.name,
                serviceName = service.name,
                price = offer.decimalPrice(),
                durationMinutes = offer.durationMinutes,
                description = offer.description,
            )
        }
        return ProviderSummary(
            name = provider.name,
            email = provider.email,
            phone = provider.phone,
            maskedCnpj = provider.cnpj.maskDocument(),
            address = AddressSummary(
                street = address.street,
                number = address.number,
                neighborhood = address.neighborhood,
                city = address.city,
                state = address.state,
            ),
            offers = offers,
        )
    }

    private suspend fun loadAllCategories(activeOnly: Boolean = false): List<ServiceCategory> {
        val result = mutableListOf<ServiceCategory>()
        var start = 0L
        do {
            val page = client.from(SERVICE_CATEGORIES).select {
                if (activeOnly) filter { eq("active", true) }
                order("name", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(start, start + PAGE_SIZE - 1)
            }.decodeList<ServiceCategoryDto>()
            result += page.map(ServiceCategoryDto::toDomain)
            start += page.size
        } while (page.size == PAGE_SIZE.toInt())
        return result.sortedWith(
            compareBy<ServiceCategory> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id },
        )
    }

    private suspend fun loadAllServices(categoryId: String?, selectableOnly: Boolean = false): List<CatalogService> {
        if (selectableOnly && categoryId != null && client.from(SERVICE_CATEGORIES).select {
            filter { eq("id", categoryId); eq("active", true) }
            limit(1)
        }.decodeList<ServiceCategoryDto>().isEmpty()) return emptyList()
        val result = mutableListOf<CatalogService>()
        var start = 0L
        do {
            val page = client.from(SERVICES).select {
                if (selectableOnly) filter { eq("active", true); eq("dispatch_kind", "BROWSE") }
                categoryId?.let { filter { eq("category_id", it) } }
                order("name", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(start, start + PAGE_SIZE - 1)
            }.decodeList<CatalogServiceDto>()
            result += page.map(CatalogServiceDto::toDomain)
            start += page.size
        } while (page.size == PAGE_SIZE.toInt())
        return result.sortedWith(
            compareBy<CatalogService> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id },
        )
    }

    private suspend fun loadAllProviderOffers(userId: String): List<ProviderOfferDto> {
        val result = mutableListOf<ProviderOfferDto>()
        var start = 0L
        do {
            val page = client.from(PROVIDER_SERVICES).select {
                filter { eq("provider_id", userId) }
                order("service_id", Order.ASCENDING)
                range(start, start + PAGE_SIZE - 1)
            }.decodeList<ProviderOfferDto>()
            result += page
            start += page.size
        } while (page.size == PAGE_SIZE.toInt())
        return result
    }

    private fun classifyRegistrationFailure(error: Throwable): ProviderRegistrationResult {
        if (error is PostgrestRestException && error.code == UNIQUE_VIOLATION) {
            return when {
                error.error.contains(PROVIDER_EMAIL_UNIQUE) -> ProviderRegistrationResult.EmailUnavailable
                error.error.contains(PROVIDER_CNPJ_UNIQUE) -> ProviderRegistrationResult.CnpjUnavailable
                error.error.contains(PROVIDER_SERVICE_UNIQUE) -> ProviderRegistrationResult.DuplicateService
                else -> ProviderRegistrationResult.Failure(ProviderFailure.Contract)
            }
        }
        return ProviderRegistrationResult.Failure(classifyFailure(error))
    }

    private fun classifyFailure(error: Throwable): ProviderFailure = when (error) {
        ProviderContractException -> ProviderFailure.Contract
        is HttpRequestTimeoutException -> ProviderFailure.Timeout
        is HttpRequestException -> ProviderFailure.Offline
        is PostgrestRestException -> when {
            error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                ProviderFailure.SessionExpired
            error.code == "P0002" -> ProviderFailure.CatalogChanged
            error.code == "23503" -> ProviderFailure.CatalogChanged
            error.code == "22023" || error.code == "23514" -> ProviderFailure.Contract
            error.statusCode >= 500 -> ProviderFailure.ServiceUnavailable
            else -> ProviderFailure.Unknown
        }
        else -> ProviderFailure.Unknown
    }

    private data object ProviderContractException : RuntimeException()

    private companion object {
        const val PROVIDERS = "providers"
        const val ADDRESSES = "addresses"
        const val SERVICE_CATEGORIES = "service_categories"
        const val SERVICES = "services"
        const val PROVIDER_SERVICES = "provider_services"
        const val CREATE_PROVIDER_REGISTRATION = "create_provider_registration"
        const val SET_PROVIDER_AVAILABILITY = "set_provider_availability"
        const val OUTCOME_ALREADY_REGISTERED = "ALREADY_REGISTERED"
        const val UNIQUE_VIOLATION = "23505"
        const val PROVIDER_EMAIL_UNIQUE = "providers_email_unique"
        const val PROVIDER_CNPJ_UNIQUE = "providers_cnpj_unique"
        const val PROVIDER_SERVICE_UNIQUE = "provider_services_provider_service_unique"
        const val PAGE_SIZE = 100L
    }
}

private fun String.maskDocument(): String = buildString {
    append("••.•••.•••/••••-")
    append(takeLast(2))
}
