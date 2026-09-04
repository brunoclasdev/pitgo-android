package com.trustarx.pitgo.features.providertracking.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState
import com.trustarx.pitgo.features.providertracking.service.ProviderLocationTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProviderTrackingStateRepository(private val context: Context) {
    private val _state = MutableStateFlow<ProviderTrackingState>(ProviderTrackingState.Stopped)
    val state: StateFlow<ProviderTrackingState> = _state.asStateFlow()

    fun start() {
        if (_state.value !is ProviderTrackingState.Stopped) return
        _state.value = ProviderTrackingState.Starting
        ContextCompat.startForegroundService(
            context,
            Intent(context, ProviderLocationTrackingService::class.java)
                .setAction(ProviderLocationTrackingService.ACTION_START),
        )
    }

    fun stop() {
        if (_state.value == ProviderTrackingState.Stopped) return
        _state.value = ProviderTrackingState.Stopping
        context.startService(
            Intent(context, ProviderLocationTrackingService::class.java)
                .setAction(ProviderLocationTrackingService.ACTION_STOP),
        )
    }

    /**
     * Cancels any legacy tracking/alert job without an RPC or a remote availability change — used
     * when a session's effective mode is PitTec's mocked prototype (specs/017-pittec-mocked-ui),
     * which must never resume the real dispatch/tracking stack. Safe to call even when nothing is
     * running: the service action itself is a no-op in that case.
     */
    fun stopLocally() {
        if (_state.value == ProviderTrackingState.Stopped) return
        context.startService(
            Intent(context, ProviderLocationTrackingService::class.java)
                .setAction(ProviderLocationTrackingService.ACTION_STOP_LOCAL),
        )
    }

    internal fun update(state: ProviderTrackingState) { _state.value = state }
}
