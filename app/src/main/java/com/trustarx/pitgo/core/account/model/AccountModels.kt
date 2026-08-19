package com.trustarx.pitgo.core.account.model

import com.trustarx.pitgo.core.vehicle.model.Vehicle
import java.net.URI
import java.util.Locale

data class Profile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val cpf: String?,
    val cnpj: String?,
    val avatarUrl: String?,
    val active: Boolean,
) {
    val displayName: String = name.trim()

    val initials: String = displayName
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .mapNotNull(String::firstOrNull)
        .joinToString("")
        .uppercase(Locale.ROOT)
        .ifBlank { "?" }

    val hasValidAvatar: Boolean
        get() = avatarUrl?.let { value ->
            runCatching {
                val uri = URI(value)
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        } == true
}

data class AccountSnapshot(
    val userId: String,
    val authEmail: String,
    val profile: Profile,
    val primaryVehicle: Vehicle?,
) {
    fun isConsistent(): Boolean = profile.id == userId &&
        profile.email.equals(authEmail, ignoreCase = true) &&
        profile.displayName.isNotBlank() &&
        profile.email.isNotBlank() &&
        profile.phone.isNotBlank() &&
        ((profile.cpf == null) xor (profile.cnpj == null)) &&
        profile.active &&
        (primaryVehicle == null || (
            primaryVehicle.userId == userId &&
                primaryVehicle.active &&
                primaryVehicle.isPrimary
            ))
}
