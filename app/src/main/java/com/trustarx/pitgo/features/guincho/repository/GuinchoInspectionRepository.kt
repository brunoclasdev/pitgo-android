package com.trustarx.pitgo.features.guincho.repository

import android.util.Log
import com.trustarx.pitgo.BuildConfig
import com.trustarx.pitgo.features.guincho.data.*
import com.trustarx.pitgo.features.guincho.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException

class GuinchoInspectionRepository(
    private val client: SupabaseClient,
    val files: GuinchoInspectionCaptureFiles,
) {
    fun accountId(): String = client.auth.currentUserOrNull()?.id ?: throw InspectionException(InspectionFailure.SESSION)
    private fun checkAccount(account: String, generation: Long) {
        if (accountId() != account || files.generation != generation) throw InspectionException(InspectionFailure.SESSION)
    }
    suspend fun document(id: String): GuinchoInspectionDocument = call("get_guincho_inspection") {
        client.postgrest.rpc("get_guincho_inspection", buildJsonObject { put("p_appointment_id", id) })
            .decodeAs<GuinchoInspectionDocumentDto>().domain()
    }
    internal suspend fun photos(id: String, version: String?, order: Long? = null, afterId: String? = null): GuinchoInspectionPhotoPage = call("list_guincho_inspection_photos") {
        client.postgrest.rpc("list_guincho_inspection_photos", buildJsonObject {
            put("p_appointment_id", id); version?.let { put("p_version_id", it) }
            order?.let { put("p_after_order", it) }; afterId?.let { put("p_after_id", it) }; put("p_limit", 20)
        }).decodeAs<GuinchoInspectionPhotoPage>().also { page -> page.items.forEach { it.validate() } }
    }
    internal suspend fun versions(id: String, before: Int? = null): GuinchoInspectionVersionPage = call("list_guincho_inspection_versions") {
        client.postgrest.rpc("list_guincho_inspection_versions", buildJsonObject {
            put("p_appointment_id", id); before?.let { put("p_before_version", it) }; put("p_limit", 10)
        }).decodeAs<GuinchoInspectionVersionPage>()
    }
    private fun command(doc: GuinchoInspectionDocument, operation: String) = buildJsonObject {
        put("p_appointment_id", doc.appointmentId); put("p_expected_revision", doc.revision); put("p_operation_id", operation)
    }
    private suspend fun mutate(name: String, body: JsonObject): GuinchoInspectionEnvelopeDto = call(name) {
        client.postgrest.rpc(name, body).decodeAs<GuinchoInspectionEnvelopeDto>().also {
            it.inspection.domain()
            if (it.outcome == "REVISION_CONFLICT") throw InspectionException(InspectionFailure.CONFLICT)
            require(it.outcome in listOf("APPLIED", "EXISTING"))
        }
    }
    internal suspend fun prepare(doc: GuinchoInspectionDocument, point: InspectionPoint, caption: String, capturedAt: String,
        file: File, operation: String, replaces: String?): GuinchoInspectionEnvelopeDto = mutate("prepare_guincho_inspection_photo",
        JsonObject(command(doc, operation) + buildJsonObject {
            put("p_version_id", requireNotNull(doc.version).id); put("p_point", point.name)
            put("p_caption", caption.trim()); put("p_captured_at", capturedAt); put("p_byte_size", file.length())
            replaces?.let { put("p_replaces_photo_id", it) }
        }))
    suspend fun upload(account: String, photo: GuinchoInspectionPhoto, file: File) = call("upload") {
        val generation = files.generation
        checkAccount(account, generation)
        withContext(Dispatchers.IO) {
            try {
                client.storage.from("guincho-inspections").upload(photo.objectPath, file.readBytes()) {
                    upsert = false; contentType = ContentType.Image.JPEG
                }
            } catch (error: RestException) {
                // A lost upload response is recovered by confirming this exact immutable object.
                if (error.statusCode != 409 && !error.message.orEmpty().contains("already exists", true) &&
                    !error.message.orEmpty().contains("Duplicate", true)) throw error
            }
        }
        checkAccount(account, generation)
    }
    suspend fun photoCommand(name: String, doc: GuinchoInspectionDocument, photoId: String, operation: String): GuinchoInspectionDocument =
        mutate(name, JsonObject(command(doc, operation) + buildJsonObject { put("p_photo_id", photoId) })).inspection.domain()
    suspend fun submit(doc: GuinchoInspectionDocument, operation: String): GuinchoInspectionDocument =
        mutate("submit_guincho_inspection", JsonObject(command(doc, operation) + buildJsonObject { put("p_version_id", requireNotNull(doc.version).id) })).inspection.domain()
    suspend fun decide(doc: GuinchoInspectionDocument, operation: String, approve: Boolean, reason: String): GuinchoInspectionDocument =
        mutate("decide_guincho_inspection", JsonObject(command(doc, operation) + buildJsonObject {
            put("p_version_id", requireNotNull(doc.version).id); put("p_decision", if (approve) "APPROVE" else "REQUEST_CORRECTION")
            if (!approve) put("p_reason", reason.trim())
        })).inspection.domain()
    suspend fun beginCorrection(doc: GuinchoInspectionDocument): GuinchoInspectionDocument = mutate("begin_guincho_inspection_correction", buildJsonObject {
        put("p_appointment_id", doc.appointmentId); put("p_expected_revision", doc.revision)
        put("p_parent_version_id", requireNotNull(doc.version).id)
    }).inspection.domain()
    suspend fun download(photo: GuinchoInspectionPhoto, account: String): File = call("download") {
        val generation = files.generation
        checkAccount(account, generation)
        // Authoritative membership is revalidated before returning even a locally cached image.
        document(photo.appointmentId)
        val file = files.downloadFile(account, photo.id)
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() != photo.byteSize) {
                val bytes = client.storage.from("guincho-inspections").downloadAuthenticated(photo.objectPath)
                checkAccount(account, generation)
                if (bytes.size.toLong() != photo.byteSize) throw InspectionException(InspectionFailure.IMAGE)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }
            checkAccount(account, generation)
            file.setLastModified(System.currentTimeMillis())
            files.trimDownloads(account, file)
        }
        file
    }
    private suspend fun <T> call(operation: String, block: suspend () -> T): T = try {
        withTimeout(60_000) {
            try { block() } catch (error: Exception) {
                if (BuildConfig.DEBUG && error !is CancellationException) Log.w("GuinchoInspection",
                    "operation=$operation exception=${error.javaClass.simpleName} http_status=${(error as? RestException)?.statusCode}")
                throw error
            }
        }
    } catch (error: InspectionException) { throw error
    } catch (_: TimeoutCancellationException) { throw InspectionException(InspectionFailure.TIMEOUT)
    } catch (cancel: CancellationException) { throw cancel
    } catch (_: IOException) { throw InspectionException(InspectionFailure.OFFLINE)
    } catch (error: RestException) {
        val message = error.message.orEmpty()
        throw InspectionException(when {
            error.statusCode == 401 -> InspectionFailure.SESSION
            error.statusCode == 403 || message.contains("PARTICIPANT_REQUIRED") -> InspectionFailure.FORBIDDEN
            message.contains("CONFLICT") -> InspectionFailure.CONFLICT
            message.contains("CLOSED") || message.contains("INVALID_TRANSITION") -> InspectionFailure.CLOSED
            message.contains("PHOTO_NOT_SAVED") || message.contains("POINTS_MISSING") || message.contains("UPLOAD_PENDING") -> InspectionFailure.INCOMPLETE
            message.contains("IMAGE") || message.contains("FILE_TOO_LARGE") -> InspectionFailure.IMAGE
            else -> InspectionFailure.UNKNOWN
        })
    } catch (_: IllegalArgumentException) { throw InspectionException(InspectionFailure.UNKNOWN)
    } catch (_: Exception) { throw InspectionException(InspectionFailure.UNKNOWN) }
}
