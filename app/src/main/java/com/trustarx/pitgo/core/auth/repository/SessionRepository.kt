package com.trustarx.pitgo.core.auth.repository

import com.trustarx.pitgo.core.account.repository.AccountContextLoadResult
import com.trustarx.pitgo.core.account.repository.AccountContextRepository
import com.trustarx.pitgo.core.auth.data.AuthFailure
import com.trustarx.pitgo.core.auth.data.AuthOperationResult
import com.trustarx.pitgo.core.auth.data.SupabaseAuthDataSource
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.model.ProfileBlockedReason
import com.trustarx.pitgo.core.auth.model.UnauthenticatedReason
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SessionLoginFailure {
    INVALID_CREDENTIALS, RATE_LIMITED, OFFLINE, TIMEOUT, PROFILE_BLOCKED,
    SERVICE_UNAVAILABLE, UNKNOWN,
}

sealed interface SessionLoginResult {
    data object Success : SessionLoginResult
    data class Failure(val reason: SessionLoginFailure) : SessionLoginResult
}

class SessionRepository(
    private val authDataSource: SupabaseAuthDataSource,
    private val accountRepository: AccountContextRepository,
    private val beforeSignOut: suspend () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bootstrapMutex = Mutex()
    private val _state = MutableStateFlow<AppSessionState>(AppSessionState.Initializing)
    val state: StateFlow<AppSessionState> = _state.asStateFlow()

    init {
        scope.launch {
            authDataSource.sessionStatus.collectLatest { status ->
                when (status) {
                    is SessionStatus.NotAuthenticated -> if (
                        _state.value !is AppSessionState.Initializing && !bootstrapMutex.isLocked
                    ) {
                        _state.value = AppSessionState.Unauthenticated(
                            if (status.isSignOut) UnauthenticatedReason.SIGNED_OUT
                            else UnauthenticatedReason.INVALID_OR_EXPIRED,
                        )
                    }
                    is SessionStatus.RefreshFailure ->
                        _state.value = AppSessionState.OfflineVerification
                    else -> Unit
                }
            }
        }
    }

    suspend fun initialize() = bootstrapMutex.withLock {
        _state.value = AppSessionState.Initializing
        authDataSource.awaitInitialization()
        if (authDataSource.currentIdentityOrNull() == null) {
            _state.value = AppSessionState.Unauthenticated()
        } else {
            verifyAndLoad()
        }
    }

    suspend fun retry() = bootstrapMutex.withLock { verifyAndLoad() }
    suspend fun refresh() = bootstrapMutex.withLock { verifyAndLoad() }

    suspend fun signIn(email: String, password: String): SessionLoginResult =
        bootstrapMutex.withLock {
            _state.value = AppSessionState.Verifying
            when (val result = authDataSource.signIn(email, password)) {
                is AuthOperationResult.Success -> {
                    loadAccount(result.identity.id, result.identity.email)
                    when (_state.value) {
                        is AppSessionState.Authenticated -> SessionLoginResult.Success
                        is AppSessionState.ProfileBlocked ->
                            SessionLoginResult.Failure(SessionLoginFailure.PROFILE_BLOCKED)
                        AppSessionState.OfflineVerification ->
                            SessionLoginResult.Failure(SessionLoginFailure.OFFLINE)
                        else -> SessionLoginResult.Failure(SessionLoginFailure.UNKNOWN)
                    }
                }
                is AuthOperationResult.Failure -> {
                    if (result.reason == AuthFailure.INVALID_SESSION) {
                        authDataSource.clearLocalSession()
                    }
                    _state.value = AppSessionState.Unauthenticated(
                        UnauthenticatedReason.INVALID_OR_EXPIRED,
                    )
                    SessionLoginResult.Failure(result.reason.toLoginFailure())
                }
            }
        }

    suspend fun signOut() {
        beforeSignOut()
        _state.value = AppSessionState.Unauthenticated(UnauthenticatedReason.SIGNED_OUT)
        authDataSource.clearLocalSession()
    }

    private suspend fun verifyAndLoad() {
        _state.value = AppSessionState.Verifying
        when (val result = authDataSource.validateCurrentIdentity()) {
            is AuthOperationResult.Success -> loadAccount(result.identity.id, result.identity.email)
            is AuthOperationResult.Failure -> when (result.reason) {
                AuthFailure.OFFLINE, AuthFailure.TIMEOUT, AuthFailure.SERVICE_UNAVAILABLE ->
                    _state.value = AppSessionState.OfflineVerification
                else -> {
                    authDataSource.clearLocalSession()
                    _state.value = AppSessionState.Unauthenticated(
                        UnauthenticatedReason.INVALID_OR_EXPIRED,
                    )
                }
            }
        }
    }

    private suspend fun loadAccount(userId: String, authEmail: String) {
        _state.value = when (val result = accountRepository.load(userId, authEmail)) {
            is AccountContextLoadResult.Client -> AppSessionState.Authenticated.Client(
                result.account, result.legacyProviderMigrationAvailable,
            )
            is AccountContextLoadResult.ProviderOnboarding ->
                AppSessionState.Authenticated.ProviderOnboarding(result.session)
            is AccountContextLoadResult.ProviderReady ->
                AppSessionState.Authenticated.ProviderReady(result.account)
            is AccountContextLoadResult.Blocked -> AppSessionState.ProfileBlocked(result.reason)
            AccountContextLoadResult.Offline, AccountContextLoadResult.Timeout,
            AccountContextLoadResult.ServiceUnavailable -> AppSessionState.OfflineVerification
            AccountContextLoadResult.Unauthorized -> {
                authDataSource.clearLocalSession()
                AppSessionState.Unauthenticated(UnauthenticatedReason.INVALID_OR_EXPIRED)
            }
            AccountContextLoadResult.Unknown -> AppSessionState.ProfileBlocked(
                ProfileBlockedReason.INCONSISTENT,
            )
        }
    }

    private fun AuthFailure.toLoginFailure(): SessionLoginFailure = when (this) {
        AuthFailure.INVALID_CREDENTIALS -> SessionLoginFailure.INVALID_CREDENTIALS
        AuthFailure.RATE_LIMITED -> SessionLoginFailure.RATE_LIMITED
        AuthFailure.OFFLINE -> SessionLoginFailure.OFFLINE
        AuthFailure.TIMEOUT -> SessionLoginFailure.TIMEOUT
        AuthFailure.SERVICE_UNAVAILABLE -> SessionLoginFailure.SERVICE_UNAVAILABLE
        AuthFailure.INVALID_SESSION, AuthFailure.UNKNOWN -> SessionLoginFailure.UNKNOWN
    }
}
