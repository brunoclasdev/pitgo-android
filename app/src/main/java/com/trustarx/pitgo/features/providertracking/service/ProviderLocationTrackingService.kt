package com.trustarx.pitgo.features.providertracking.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.trustarx.pitgo.PitGoApplication
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.features.providertracking.model.ProviderLocationUpdate
import com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState
import com.trustarx.pitgo.features.providertracking.model.PublishLocationResult
import com.trustarx.pitgo.features.providertracking.model.TrackingStopReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class ProviderLocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private var appointmentId: String? = null
    private var trackerId: String? = null

    private val container get() = (application as PitGoApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTracking()
            ACTION_START -> startTracking(intent.getStringExtra(EXTRA_APPOINTMENT_ID))
        }
        return START_NOT_STICKY
    }

    private fun startTracking(requestedAppointmentId: String?) {
        if (requestedAppointmentId.isNullOrBlank() || trackingJob != null) { stopSelf(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            container.providerTrackingStateRepository.update(
                ProviderTrackingState.Degraded(requestedAppointmentId, TrackingStopReason.PERMISSION_REVOKED),
            )
            stopSelf(); return
        }
        ensureChannel()
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ProviderLocationTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_message))
            .setOngoing(true)
            .addAction(0, getString(R.string.tracking_stop), stopIntent)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else 0,
        )
        appointmentId = requestedAppointmentId
        trackerId = UUID.randomUUID().toString()
        trackingJob = scope.launch {
            val session = container.sessionRepository.state.value
                as? AppSessionState.Authenticated.ProviderReady
            if (session?.account?.locationMode != ProviderLocationMode.MOBILE ||
                !session.account.available) {
                finishWith(TrackingStopReason.INELIGIBLE); return@launch
            }
            container.providerTrackingStateRepository.update(
                ProviderTrackingState.Active(requestedAppointmentId, null),
            )
            var sequence = 0L
            try {
                launch {
                    container.sessionRepository.state.collectLatest { current ->
                        val provider = current as? AppSessionState.Authenticated.ProviderReady
                        if (provider?.account?.locationMode != ProviderLocationMode.MOBILE ||
                            !provider.account.available
                        ) finishWith(TrackingStopReason.SESSION_EXPIRED)
                    }
                }
                container.deviceLocationRepository.preciseUpdates().collectLatest { fix ->
                    sequence += 1
                    when (container.providerLocationRepository.publish(
                        ProviderLocationUpdate(
                            requestedAppointmentId, requireNotNull(trackerId), sequence, fix,
                        ),
                    )) {
                        is PublishLocationResult.Published -> container.providerTrackingStateRepository.update(
                            ProviderTrackingState.Active(requestedAppointmentId, System.currentTimeMillis()),
                        )
                        PublishLocationResult.Offline -> container.providerTrackingStateRepository.update(
                            ProviderTrackingState.Degraded(requestedAppointmentId, TrackingStopReason.OFFLINE),
                        )
                        PublishLocationResult.Conflict -> finishWith(TrackingStopReason.PUBLISHER_CONFLICT)
                        PublishLocationResult.SessionExpired -> finishWith(TrackingStopReason.SESSION_EXPIRED)
                        PublishLocationResult.Ineligible -> finishWith(TrackingStopReason.INELIGIBLE)
                        else -> Unit
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
              catch (_: SecurityException) { finishWith(TrackingStopReason.PERMISSION_REVOKED) }
              catch (_: Throwable) { finishWith(TrackingStopReason.UNKNOWN) }
        }
    }

    private fun stopTracking() {
        val appointment = appointmentId
        val tracker = trackerId
        trackingJob?.cancel(); trackingJob = null
        scope.launch {
            if (appointment != null && tracker != null) {
                container.providerLocationRepository.stop(appointment, tracker)
            }
            container.providerTrackingStateRepository.update(ProviderTrackingState.Stopped)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun finishWith(reason: TrackingStopReason) {
        val appointment = appointmentId ?: return
        container.providerTrackingStateRepository.update(ProviderTrackingState.Degraded(appointment, reason))
        trackingJob?.cancel(); trackingJob = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "com.trustarx.pitgo.action.START_PROVIDER_TRACKING"
        const val ACTION_STOP = "com.trustarx.pitgo.action.STOP_PROVIDER_TRACKING"
        const val EXTRA_APPOINTMENT_ID = "appointment_id"
        private const val CHANNEL_ID = "provider_location_tracking"
        private const val NOTIFICATION_ID = 7001
    }
}
