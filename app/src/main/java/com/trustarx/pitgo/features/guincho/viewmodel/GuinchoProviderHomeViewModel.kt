package com.trustarx.pitgo.features.guincho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.location.model.*
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.map.model.*
import com.trustarx.pitgo.features.guincho.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class GuinchoProviderHomeViewModel(private val locations: DeviceLocationRepository) : ViewModel() {
    private val mutable = MutableStateFlow(GuinchoProviderHomeState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var revision = 0L
    private var nextAutoCenterNanos = 0L
    private var lastFix: DeviceLocationFix? = null
    fun start(permitted: Boolean) {
        stop()
        nextAutoCenterNanos = System.nanoTime() + AUTO_CENTER_INTERVAL_NANOS
        mutable.value = mutable.value.copy(follow = true)
        if (!permitted) { mutable.value = mutable.value.copy(position = GuinchoProviderPosition.PermissionDenied); return }
        mutable.value = mutable.value.copy(position = lastFix?.let {
            if (it.isHighQuality(System.currentTimeMillis())) GuinchoProviderPosition.Current(it)
            else GuinchoProviderPosition.Stale(it)
        } ?: GuinchoProviderPosition.Loading)
        job = viewModelScope.launch {
            val startedNanos = System.nanoTime()
            launch {
                while (isActive) {
                    val fix = lastFix
                    val position = when {
                        !locations.hasPrecisePermission() -> GuinchoProviderPosition.PermissionDenied
                        !locations.areLocationServicesEnabled() -> GuinchoProviderPosition.LocationDisabled
                        fix != null && fix.isHighQuality(System.currentTimeMillis()) -> GuinchoProviderPosition.Current(fix)
                        fix != null -> GuinchoProviderPosition.Stale(fix)
                        System.nanoTime() - startedNanos > 15_000_000_000L -> GuinchoProviderPosition.Unavailable
                        else -> GuinchoProviderPosition.Loading
                    }
                    mutable.value = mutable.value.copy(position = position)
                    if (System.nanoTime() >= nextAutoCenterNanos) {
                        nextAutoCenterNanos = System.nanoTime() + AUTO_CENTER_INTERVAL_NANOS
                        mutable.value = mutable.value.copy(follow = true)
                        (mutable.value.position as? GuinchoProviderPosition.Current)?.let { move(it.fix) }
                    }
                    delay(1_000)
                }
            }
            try { locations.preciseUpdates().collect(::update) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: SecurityException) { mutable.value = mutable.value.copy(position = GuinchoProviderPosition.PermissionDenied) }
            catch (_: Exception) { mutable.value = mutable.value.copy(position = GuinchoProviderPosition.Unavailable) }
        }
    }
    fun stop() { job?.cancel(); job = null }
    private fun update(fix: DeviceLocationFix) {
        if (!locations.hasPrecisePermission() || !locations.areLocationServicesEnabled() ||
            !fix.isHighQuality(System.currentTimeMillis())) return
        lastFix = fix
        mutable.value = mutable.value.copy(position = GuinchoProviderPosition.Current(fix))
        if (mutable.value.follow) move(fix)
    }
    private fun move(fix: DeviceLocationFix) {
        val viewport = MapViewport(MapCoordinate(fix.latitude, fix.longitude), mutable.value.viewport.zoom.coerceAtLeast(15.0))
        mutable.value = mutable.value.copy(viewport = viewport, camera = MapViewportCommand(viewport, ++revision))
    }
    fun gesture() {
        mutable.value = mutable.value.copy(follow = false)
        nextAutoCenterNanos = System.nanoTime() + AUTO_CENTER_INTERVAL_NANOS
    }
    fun viewport(value: MapViewport) { mutable.value = mutable.value.copy(viewport = value) }
    fun surface(value: MapSurfaceState) { mutable.value = mutable.value.copy(surface = value) }
    fun retryMap() { mutable.value = mutable.value.copy(styleRevision = mutable.value.styleRevision + 1) }
    companion object {
        private const val AUTO_CENTER_INTERVAL_NANOS = 30_000_000_000L
        fun factory(repository: DeviceLocationRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GuinchoProviderHomeViewModel(repository) as T
        }
    }
}
