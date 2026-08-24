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

    internal fun update(state: ProviderTrackingState) { _state.value = state }
}
