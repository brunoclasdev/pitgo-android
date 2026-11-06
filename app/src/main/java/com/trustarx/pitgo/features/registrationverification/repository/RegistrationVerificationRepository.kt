package com.trustarx.pitgo.features.registrationverification.repository

import com.trustarx.pitgo.features.registrationverification.data.*
import com.trustarx.pitgo.features.registrationverification.model.DocumentKind
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.json.*
import java.util.UUID
import io.ktor.client.call.body

class RegistrationVerificationRepository(private val client: SupabaseClient) {
    private val json = Json { ignoreUnknownKeys = true }
    fun email(): String = client.auth.currentUserOrNull()?.email.orEmpty()
    fun userId(): String = requireNotNull(client.auth.currentUserOrNull()?.id)
    suspend fun basic(mode: String, first: String, last: String, email: String, phone: String, document: String, password: String) {
        val existing = client.auth.currentUserOrNull()
        if (existing?.email.equals(email,ignoreCase=true)) {
            client.auth.retrieveUserForCurrentSession()
            return
        }
        client.auth.signUpWith(Email) {
            this.email = email; this.password = password
            data = buildJsonObject { put("account_type",mode);put("first_name",first);put("last_name",last)
                put("name","$first $last");put("phone",phone);put(if(mode=="PROVIDER" || document.length==14) "cnpj" else "cpf",document) }
        }
        check(client.auth.currentUserOrNull()!=null)
    }
    suspend fun current(mode: String): RegistrationCase = client.postgrest.rpc("get_my_registration",buildJsonObject { put("p_account_type",mode) }).decodeAs()
    suspend fun start(mode: String, operation: String): RegistrationCase = client.postgrest.rpc("start_my_registration_mode",buildJsonObject {
        put("p_account_type",mode);put("p_operation_id",operation)
    }).decodeAs()
    suspend fun save(case: RegistrationCase, data: JsonObject, operation: String): RegistrationCase = client.postgrest.rpc("save_my_registration_draft",buildJsonObject {
        put("p_account_type",case.accountType);put("p_data",data);put("p_operation_id",operation);put("p_expected_revision",case.revision)
    }).decodeAs()
    suspend fun reserve(case: RegistrationCase, kind: DocumentKind, vehicle: String?, size: Long, operation: String): EvidenceReservation = client.postgrest.rpc("reserve_my_registration_evidence",buildJsonObject {
        put("p_submission_id",case.submissionId);put("p_kind",kind.name);put("p_vehicle_reference_id",vehicle)
        put("p_mime_type","image/jpeg");put("p_byte_size",size);put("p_operation_id",operation);put("p_expected_revision",case.revision)
    }).decodeAs()
    suspend fun upload(reservation: EvidenceReservation, bytes: ByteArray) {
        client.storage.from("registration-documents").upload(reservation.path,bytes) { upsert = false; contentType = io.ktor.http.ContentType.Image.JPEG }
    }
    suspend fun confirm(case: RegistrationCase, reservation: EvidenceReservation, operation: String) {
        val response = client.functions.invoke("registration-evidence-confirm",buildJsonObject {
            put("evidence_id",reservation.evidenceId);put("operation_id",operation);put("expected_revision",reservation.revision)
        })
        check(response.status.value in 200..299)
    }
    suspend fun submit(case: RegistrationCase, operation: String): RegistrationCase = client.postgrest.rpc("submit_my_registration",buildJsonObject {
        put("p_submission_id",case.submissionId);put("p_operation_id",operation);put("p_expected_revision",case.revision)
    }).decodeAs()
    suspend fun correct(case: RegistrationCase, operation: String): RegistrationCase = client.postgrest.rpc("begin_my_registration_correction",buildJsonObject {
        put("p_case_id",case.caseId);put("p_operation_id",operation);put("p_expected_revision",case.revision)
    }).decodeAs()
    suspend fun photo(path: String): ByteArray = client.storage.from("registration-documents").downloadAuthenticated(path)
}
