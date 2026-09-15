package com.trustarx.pitgo.features.paymentmethods.repository

import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
import com.trustarx.pitgo.features.payment.data.EdgeFunctionErrorDto
import com.trustarx.pitgo.features.paymentmethods.data.RemoveCardRequestDto
import com.trustarx.pitgo.features.paymentmethods.data.SaveCardRequestDto
import com.trustarx.pitgo.features.paymentmethods.data.SaveCardResponseDto
import com.trustarx.pitgo.features.paymentmethods.data.SavedCardDto
import com.trustarx.pitgo.features.paymentmethods.model.RemoveCardResult
import com.trustarx.pitgo.features.paymentmethods.model.SaveCardResult
import com.trustarx.pitgo.features.paymentmethods.model.SavedCardFailure
import com.trustarx.pitgo.features.paymentmethods.model.SavedCardsResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.trustarx.pitgo.features.paymentmethods.data.SavedCardContextDto
import com.trustarx.pitgo.features.paymentmethods.model.CardContextResult

/** Cadastro opcional de cartão (FR-004/FR-006/FR-007) — área dedicada, sem checkout inline. */
class SavedCardRepository(private val client: SupabaseClient) {
    suspend fun tokenizationContext(savedCardId: String): CardContextResult = try {
        val dto = withTimeout(RPC_TIMEOUT_MILLIS) {
            client.postgrest.rpc("get_saved_card_tokenization_context", buildJsonObject {
                put("p_saved_card_id", savedCardId)
            }).decodeAs<SavedCardContextDto>()
        }
        CardContextResult.Success(dto.toDomain())
    } catch (_: TimeoutCancellationException) {
        CardContextResult.Failure(SavedCardFailure.TIMEOUT)
    } catch (cancel: CancellationException) { throw cancel
    } catch (error: Throwable) { CardContextResult.Failure(classifyRpcError(error)) }

    suspend fun list(): SavedCardsResult = try {
        val rows = withTimeout(RPC_TIMEOUT_MILLIS) {
            client.postgrest.rpc("list_saved_cards").decodeList<SavedCardDto>()
        }
        SavedCardsResult.Success(rows.mapNotNull(SavedCardDto::toDomainOrNull))
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: TimeoutCancellationException) {
        SavedCardsResult.Failure(SavedCardFailure.TIMEOUT)
    } catch (error: Throwable) {
        SavedCardsResult.Failure(classifyRpcError(error))
    }

    suspend fun save(cardToken: String): SaveCardResult {
        if (cardToken.isBlank()) return SaveCardResult.Failure(SavedCardFailure.CONTRACT)
        return try {
            val response = withTimeout(RPC_TIMEOUT_MILLIS) {
                client.functions.invoke(function = "mp-save-card", body = SaveCardRequestDto(cardToken))
            }
            response.body<SaveCardResponseDto>().toDomainOrNull()?.let(SaveCardResult::Success)
                ?: SaveCardResult.Failure(SavedCardFailure.CONTRACT)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: TimeoutCancellationException) {
            SaveCardResult.Failure(SavedCardFailure.TIMEOUT)
        } catch (_: HttpRequestTimeoutException) {
            SaveCardResult.Failure(SavedCardFailure.TIMEOUT)
        } catch (_: HttpRequestException) {
            SaveCardResult.Failure(SavedCardFailure.OFFLINE)
        } catch (error: RestException) {
            SaveCardResult.Failure(classifyEdgeFunctionError(error))
        } catch (_: Throwable) {
            SaveCardResult.Failure(SavedCardFailure.UNKNOWN)
        }
    }

    suspend fun remove(savedCardId: String): RemoveCardResult {
        if (!savedCardId.isUuid()) return RemoveCardResult.Failure(SavedCardFailure.CONTRACT)
        return try {
            withTimeout(RPC_TIMEOUT_MILLIS) {
                client.functions.invoke(function = "mp-remove-card", body = RemoveCardRequestDto(savedCardId))
            }
            RemoveCardResult.Success
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: TimeoutCancellationException) {
            RemoveCardResult.Failure(SavedCardFailure.TIMEOUT)
        } catch (_: HttpRequestTimeoutException) {
            RemoveCardResult.Failure(SavedCardFailure.TIMEOUT)
        } catch (_: HttpRequestException) {
            RemoveCardResult.Failure(SavedCardFailure.OFFLINE)
        } catch (error: RestException) {
            RemoveCardResult.Failure(classifyEdgeFunctionError(error))
        } catch (_: Throwable) {
            RemoveCardResult.Failure(SavedCardFailure.UNKNOWN)
        }
    }

    private suspend fun classifyEdgeFunctionError(error: RestException): SavedCardFailure {
        val body = runCatching { error.response.body<EdgeFunctionErrorDto>() }.getOrNull()
        return when {
            error.statusCode == 401 -> SavedCardFailure.SESSION_EXPIRED
            body?.code == "CARD_REJECTED" -> SavedCardFailure.CARD_REJECTED
            body?.code == "CARD_NOT_FOUND" -> SavedCardFailure.CARD_NOT_FOUND
            error.statusCode == 422 -> SavedCardFailure.CARD_REJECTED
            error.statusCode >= 500 -> SavedCardFailure.SERVICE_UNAVAILABLE
            else -> SavedCardFailure.CONTRACT
        }
    }

    private fun classifyRpcError(error: Throwable): SavedCardFailure = when (error) {
        is HttpRequestTimeoutException -> SavedCardFailure.TIMEOUT
        is HttpRequestException -> SavedCardFailure.OFFLINE
        is PostgrestRestException -> when {
            error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                SavedCardFailure.SESSION_EXPIRED
            error.statusCode >= 500 -> SavedCardFailure.SERVICE_UNAVAILABLE
            else -> SavedCardFailure.CONTRACT
        }
        else -> SavedCardFailure.UNKNOWN
    }

    private companion object {
        const val RPC_TIMEOUT_MILLIS = 15_000L
    }
}
