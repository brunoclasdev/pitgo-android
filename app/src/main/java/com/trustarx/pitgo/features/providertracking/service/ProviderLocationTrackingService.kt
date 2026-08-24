package com.trustarx.pitgo.features.providertracking.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.trustarx.pitgo.MainActivity
import com.trustarx.pitgo.PitGoApplication
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUpdateResult
import com.trustarx.pitgo.features.providertracking.model.ProviderLocationUpdate
import com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState
import com.trustarx.pitgo.features.providertracking.model.ProviderTrackingTarget
import com.trustarx.pitgo.features.providertracking.model.PublishLocationResult
import com.trustarx.pitgo.features.providertracking.model.TrackingStopReason
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class ProviderLocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private var trackerId: String? = null
    private var watchdogJob: Job? = null
    private var lastPublishedAt: Long? = null
    private var lastPreciseFixAt: Long? = null
    private val container get() = (application as PitGoApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTracking(updateAvailability = true)
            ACTION_START, null -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (trackingJob != null) return
        lastPreciseFixAt = null
        ensureForeground()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            degradeAndStop(TrackingStopReason.PERMISSION_REVOKED)
            return
        }
        trackingJob = scope.launch {
            container.sessionRepository.ensureInitialized()
            val session = container.sessionRepository.state.value as? AppSessionState.Authenticated.ProviderReady
            if (session?.account?.locationMode != ProviderLocationMode.MOBILE || !session.account.available) {
                degradeAndStop(TrackingStopReason.INELIGIBLE)
                return@launch
            }
            val lease = container.providerTrackingPreferences.lease(session.userId)
            trackerId = lease.trackerId
            var sequence = lease.sequence
            container.providerTrackingStateRepository.update(ProviderTrackingState.Starting)
            watchdogJob?.cancel()
            watchdogJob = scope.launch {
                while (true) {
                    delay(10_000)
                    val lastFix = lastPreciseFixAt
                    if (lastFix == null || System.currentTimeMillis() - lastFix > 30_000L) {
                        degradeAndStop(TrackingStopReason.NO_PRECISE_FIX)
                        return@launch
                    }
                }
            }
            try {
                container.deviceLocationRepository.preciseUpdates().conflate().collect { fix ->
                    lastPreciseFixAt = System.currentTimeMillis()
                    val current = container.sessionRepository.state.value
                        as? AppSessionState.Authenticated.ProviderReady
                    if (current?.userId != session.userId || current.account.locationMode != ProviderLocationMode.MOBILE ||
                        !current.account.available) {
                        degradeAndStop(TrackingStopReason.SESSION_EXPIRED)
                        return@collect
                    }
                    sequence += 1
                    when (val result = container.providerLocationRepository.publish(
                        ProviderLocationUpdate(lease.trackerId, sequence, fix),
                    )) {
                        is PublishLocationResult.Published -> {
                            container.providerTrackingPreferences.updateSequence(session.userId, lease.trackerId, sequence)
                            lastPublishedAt = System.currentTimeMillis()
                            container.providerTrackingStateRepository.update(
                                ProviderTrackingState.Publishing(
                                    result.target,
                                    lastPublishedAt,
                                ),
                            )
                        }
                        PublishLocationResult.Offline -> container.providerTrackingStateRepository.update(
                            ProviderTrackingState.Degraded(
                                ProviderTrackingTarget.DISCOVERY,
                                TrackingStopReason.OFFLINE,
                                lastPublishedAt,
                            ),
                        )
                        PublishLocationResult.Conflict -> degradeAndStop(TrackingStopReason.PUBLISHER_CONFLICT)
                        PublishLocationResult.SessionExpired -> degradeAndStop(TrackingStopReason.SESSION_EXPIRED)
                        PublishLocationResult.Ineligible -> degradeAndStop(TrackingStopReason.INELIGIBLE)
                        PublishLocationResult.Failed -> container.providerTrackingStateRepository.update(
                            ProviderTrackingState.Degraded(
                                ProviderTrackingTarget.DISCOVERY,
                                TrackingStopReason.UNKNOWN,
                                lastPublishedAt,
                            ),
                        )
                        PublishLocationResult.Ignored -> Unit
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: SecurityException) { degradeAndStop(TrackingStopReason.PERMISSION_REVOKED) }
            catch (_: Throwable) { degradeAndStop(TrackingStopReason.UNKNOWN) }
        }
    }

    private fun stopTracking(updateAvailability: Boolean) {
        if (container.providerTrackingStateRepository.state.value !is ProviderTrackingState.Stopped) {
            container.providerTrackingStateRepository.update(ProviderTrackingState.Stopping)
        }
        val tracker = trackerId
        trackingJob?.cancel(); trackingJob = null
        watchdogJob?.cancel(); watchdogJob = null
        scope.launch {
            if (updateAvailability) {
                val dispatch = container.mobileDispatchRepository.restore()
                val activeAppointment = (dispatch as? MobileDispatchResult.Success)?.snapshot?.appointment
                if (activeAppointment?.status in setOf(MobileAppointmentStatus.ACCEPTED, MobileAppointmentStatus.IN_PROGRESS)) {
                    startActivity(Intent(this@ProviderLocationTrackingService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    container.providerTrackingStateRepository.update(
                        ProviderTrackingState.Publishing(ProviderTrackingTarget.APPOINTMENT, lastPublishedAt),
                    )
                    startTracking()
                    return@launch
                }
                val current = container.sessionRepository.state.value as? AppSessionState.Authenticated.ProviderReady
                if (current != null) {
                    when (val updated = container.providerDashboardRepository.update(
                        current.account,
                        available = false,
                        mode = current.account.locationMode ?: ProviderLocationMode.MOBILE,
                    )) {
                        is ProviderDashboardUpdateResult.Success ->
                            container.sessionRepository.applyProviderSnapshot(updated.provider)
                        ProviderDashboardUpdateResult.Failure -> Unit
                    }
                }
            }
            if (tracker != null) container.providerLocationRepository.stop(tracker)
            container.providerTrackingPreferences.clear()
            container.providerTrackingStateRepository.update(ProviderTrackingState.Stopped)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun degradeAndStop(reason: TrackingStopReason) {
        container.providerTrackingStateRepository.update(
            ProviderTrackingState.Degraded(ProviderTrackingTarget.DISCOVERY, reason, lastPublishedAt),
        )
        val tracker = trackerId
        trackingJob?.cancel(); trackingJob = null
        watchdogJob?.cancel(); watchdogJob = null
        scope.launch {
            val current = container.sessionRepository.state.value as? AppSessionState.Authenticated.ProviderReady
            if (current != null) {
                when (val updated = container.providerDashboardRepository.update(
                    current.account,
                    available = false,
                    mode = current.account.locationMode ?: ProviderLocationMode.MOBILE,
                )) {
                    is ProviderDashboardUpdateResult.Success ->
                        container.sessionRepository.applyProviderSnapshot(updated.provider)
                    ProviderDashboardUpdateResult.Failure -> Unit
                }
            }
            if (tracker != null) container.providerLocationRepository.stop(tracker)
            container.providerTrackingPreferences.clear()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.tracking_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val dashboard = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ProviderLocationTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_message))
            .setContentIntent(dashboard)
            .setOngoing(true)
            .addAction(0, getString(R.string.tracking_stop), stop)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "com.trustarx.pitgo.action.START_PROVIDER_TRACKING"
        const val ACTION_STOP = "com.trustarx.pitgo.action.STOP_PROVIDER_TRACKING"
        private const val CHANNEL_ID = "provider_location_tracking"
        private const val NOTIFICATION_ID = 7001
    }
}
