package com.trustarx.pitgo.core.auth.model

import com.trustarx.pitgo.core.account.model.AccountSnapshot

sealed interface AppSessionState {
    data object Initializing : AppSessionState
    data object Verifying : AppSessionState
    data class Unauthenticated(
        val reason: UnauthenticatedReason = UnauthenticatedReason.NO_SESSION,
    ) : AppSessionState
    data object OfflineVerification : AppSessionState
    data class ProfileBlocked(val reason: ProfileBlockedReason) : AppSessionState
    data class Authenticated(val account: AccountSnapshot) : AppSessionState
}

enum class UnauthenticatedReason {
    NO_SESSION,
    INVALID_OR_EXPIRED,
    SIGNED_OUT,
}

enum class ProfileBlockedReason {
    MISSING,
    INACTIVE,
    INCONSISTENT,
}
