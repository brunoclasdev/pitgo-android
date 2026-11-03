package com.trustarx.pitgo.core.vehicle.repository

import com.trustarx.pitgo.core.vehicle.data.TransportCatalogDto
import com.trustarx.pitgo.core.vehicle.model.TransportCatalogState
import com.trustarx.pitgo.core.vehicle.model.TransportCatalogFailure
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException

class TransportCatalogRepository(private val client: SupabaseClient) {
    suspend fun load(): TransportCatalogState = try {
        withTimeoutOrNull(15_000) {
            TransportCatalogState.Ready(client.postgrest.rpc("get_guincho_transport_catalog").decodeAs<TransportCatalogDto>().domain())
        } ?: TransportCatalogState.Failed(TransportCatalogFailure.TIMEOUT)
    } catch (cancel: CancellationException) { throw cancel }
      catch (_: HttpRequestTimeoutException) { TransportCatalogState.Failed(TransportCatalogFailure.TIMEOUT) }
      catch (_: HttpRequestException) { TransportCatalogState.Failed(TransportCatalogFailure.OFFLINE) }
      catch (error: RestException) { TransportCatalogState.Failed(if(error.statusCode==401) TransportCatalogFailure.SESSION_EXPIRED else TransportCatalogFailure.UNAVAILABLE) }
      catch (_: SerializationException) { TransportCatalogState.Failed(TransportCatalogFailure.CONTRACT) }
      catch (_: IllegalArgumentException) { TransportCatalogState.Failed(TransportCatalogFailure.CONTRACT) }
      catch (_: Exception) { TransportCatalogState.Failed(TransportCatalogFailure.UNAVAILABLE) }
}
