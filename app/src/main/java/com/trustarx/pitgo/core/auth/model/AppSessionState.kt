package com.trustarx.pitgo.core.auth.model

import com.trustarx.pitgo.core.account.model.AccountSnapshot
import com.trustarx.pitgo.core.account.model.ProviderOnboardingSession
import com.trustarx.pitgo.core.account.model.ProviderSnapshot

sealed interface AppSessionState {
    data object Initializing : AppSessionState
    data object Verifying : AppSessionState
    data class Unauthenticated(
        val reason: UnauthenticatedReason = UnauthenticatedReason.NO_SESSION,
    ) : AppSessionState
    data object OfflineVerification : AppSessionState
    data class ProfileBlocked(val reason: ProfileBlockedReason) : AppSessionState
    sealed interface Authenticated : AppSessionState {
        val userId: String

        data class Client(
            val account: AccountSnapshot,
            val legacyProviderMigrationAvailable: Boolean = false,
        ) : Authenticated {
            override val userId: String get() = account.userId
        }

        data class ProviderOnboarding(val session: ProviderOnboardingSession) : Authenticated {
            override val userId: String get() = session.userId
        }

        data class ProviderReady(val account: ProviderSnapshot) : Authenticated {
            override val userId: String get() = account.userId
        }
    }
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
