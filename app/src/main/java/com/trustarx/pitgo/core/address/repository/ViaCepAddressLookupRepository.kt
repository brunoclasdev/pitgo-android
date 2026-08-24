package com.trustarx.pitgo.core.address.repository

import com.trustarx.pitgo.core.address.model.PostalAddressResult
import com.trustarx.pitgo.core.address.model.PostalCodeLookupFailure
import com.trustarx.pitgo.core.address.model.PostalCodeLookupResult
import com.trustarx.pitgo.core.common.validation.BrazilianInputValidation
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

class ViaCepAddressLookupRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        }
    }

    suspend fun lookup(postalCode: String): PostalCodeLookupResult {
        val normalized = BrazilianInputValidation.normalizePostalCode(postalCode)
        if (!BrazilianInputValidation.isValidPostalCode(normalized)) {
            return PostalCodeLookupResult.Failure(PostalCodeLookupFailure.UNEXPECTED_RESPONSE)
        }

        return try {
            val response = client.get("$BASE_URL/$normalized/json/") {
                accept(ContentType.Application.Json)
            }
            when {
                response.status.isSuccess() -> decode(normalized, response.bodyAsText())
                response.status == HttpStatusCode.RequestTimeout ||
                    response.status == HttpStatusCode.GatewayTimeout ->
                    PostalCodeLookupResult.Failure(PostalCodeLookupFailure.TIMEOUT)
                response.status == HttpStatusCode.TooManyRequests ||
                    response.status.value in 500..599 ->
                    PostalCodeLookupResult.Failure(PostalCodeLookupFailure.SERVICE_UNAVAILABLE)
                else -> PostalCodeLookupResult.Failure(
                    PostalCodeLookupFailure.UNEXPECTED_RESPONSE,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HttpRequestTimeoutException) {
            PostalCodeLookupResult.Failure(PostalCodeLookupFailure.TIMEOUT)
        } catch (_: ConnectTimeoutException) {
            PostalCodeLookupResult.Failure(PostalCodeLookupFailure.TIMEOUT)
        } catch (_: SocketTimeoutException) {
            PostalCodeLookupResult.Failure(PostalCodeLookupFailure.TIMEOUT)
        } catch (_: UnknownHostException) {
            PostalCodeLookupResult.Failure(PostalCodeLookupFailure.OFFLINE)
        } catch (_: NoRouteToHostException) {
            PostalCodeLookupResult.Failure(PostalCodeLookupFailure.OFFLINE)
        } catch (_: ConnectException) {
            PostalCodeLookupResult.Failure(PostalCodeLookupFailure.SERVICE_UNAVAILABLE)
        } catch (_: SocketException) {
            PostalCodeLookupResult.Failure(PostalCodeLookupFailure.SERVICE_UNAVAILABLE)
        } catch (_: Throwable) {
            PostalCodeLookupResult.Failure(PostalCodeLookupFailure.UNEXPECTED_RESPONSE)
        }
    }

    private fun decode(requestedPostalCode: String, body: String): PostalCodeLookupResult {
        val dto = runCatching { json.decodeFromString<ViaCepResponseDto>(body) }
            .getOrElse {
                return PostalCodeLookupResult.Failure(
                    PostalCodeLookupFailure.UNEXPECTED_RESPONSE,
                )
            }
        if (dto.error.isTrue()) return PostalCodeLookupResult.NotFound

        val returnedPostalCode = BrazilianInputValidation.normalizePostalCode(dto.postalCode.orEmpty())
        val city = dto.city?.trim().orEmpty()
        val state = dto.state?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (returnedPostalCode != requestedPostalCode || city.isBlank() ||
            !BrazilianInputValidation.isValidState(state)
        ) {
            return PostalCodeLookupResult.Failure(PostalCodeLookupFailure.UNEXPECTED_RESPONSE)
        }

        return PostalCodeLookupResult.Found(
            PostalAddressResult(
                postalCode = returnedPostalCode,
                street = dto.street.cleanOptional(),
                complement = dto.complement.cleanOptional(),
                neighborhood = dto.neighborhood.cleanOptional(),
                city = city,
                state = state,
            ),
        )
    }

    private fun JsonElement?.isTrue(): Boolean = (this as? JsonPrimitive)?.let { value ->
        value.booleanOrNull == true || value.content.equals("true", ignoreCase = true)
    } == true

    private fun String?.cleanOptional(): String? = this?.trim()?.takeIf(String::isNotBlank)

    private companion object {
        const val BASE_URL = "https://viacep.com.br/ws"
        const val REQUEST_TIMEOUT_MILLIS = 2_500L
        const val CONNECT_TIMEOUT_MILLIS = 1_500L
        const val SOCKET_TIMEOUT_MILLIS = 2_000L
    }
}

@Serializable
private data class ViaCepResponseDto(
    @SerialName("cep") val postalCode: String? = null,
    @SerialName("logradouro") val street: String? = null,
    @SerialName("complemento") val complement: String? = null,
    @SerialName("bairro") val neighborhood: String? = null,
    @SerialName("localidade") val city: String? = null,
    @SerialName("uf") val state: String? = null,
    @SerialName("erro") val error: JsonElement? = null,
)
