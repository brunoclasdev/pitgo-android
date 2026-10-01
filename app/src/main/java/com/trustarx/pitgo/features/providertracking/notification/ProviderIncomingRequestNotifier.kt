package com.trustarx.pitgo.features.providertracking.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.trustarx.pitgo.MainActivity
import com.trustarx.pitgo.R

data class GuinchoNotificationOpen(val appointmentId: String, val providerId: String, val token: Long)

/**
 * Local, high-importance alert for an incoming PitTec offer (US4). See
 * contracts/pittec-realtime-notifications.md `Local notification`. There is no FCM in this
 * delivery, so this only fires while the app process (and this provider's foreground location
 * service, see ProviderLocationTrackingService) is alive.
 */
class ProviderIncomingRequestNotifier(private val context: Context) {
    private val delivery = ProviderIncomingAlertStore(context)

    suspend fun purgeExpiredDelivery() { delivery.purgeExpired() }

    suspend fun notifyGuincho(providerId: String, appointmentId: String, deadline: Long) {
        if (!isReady()) return
        delivery.post(providerId, appointmentId, deadline) { silent ->
            postIncoming(appointmentId, ((deadline - System.currentTimeMillis()) / 1000).coerceAtLeast(0), true, providerId, silent)
        }
    }

    suspend fun finishGuincho(providerId: String, appointmentId: String) {
        cancel(appointmentId)
        delivery.finish(providerId, appointmentId)
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.pittec_incoming_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.pittec_incoming_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * False when the channel has been silenced or notifications are disabled app-wide — the
     * dashboard uses this to refuse enabling PitTec availability instead of silently going
     * unreachable (see contracts/pittec-realtime-notifications.md `Availability lifecycle`).
     */
    fun isReady(): Boolean {
        // Availability is checked before the first incoming request. Create the channel here so
        // a fresh installation is not rejected merely because notifyIncoming() has never run.
        // Recreating an existing channel preserves any importance chosen by the user.
        ensureChannel()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
            return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }

    /** PitTec keeps the existing resume path; Guincho includes only IDs for stale-tap validation. */
    fun notifyIncoming(appointmentId: String, remainingSeconds: Long, guincho: Boolean = false) {
        postIncoming(appointmentId, remainingSeconds, guincho)
    }

    private fun postIncoming(appointmentId: String, remainingSeconds: Long, guincho: Boolean,
        providerId: String? = null, silent: Boolean = false): Boolean {
        if (remainingSeconds <= 0) return false
        ensureChannel()
        val id = notificationId(appointmentId)
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (guincho) { putExtra(EXTRA_APPOINTMENT, appointmentId); putExtra(EXTRA_PROVIDER, providerId) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pittec)
            .setContentTitle(context.getString(if (guincho) R.string.guincho_incoming_title else R.string.pittec_incoming_title))
            .setContentText(context.getString(R.string.pittec_incoming_body, remainingSeconds.coerceAtLeast(0)))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setTimeoutAfter(remainingSeconds * 1_000)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return postIfPermitted(id, notification)
    }

    fun cancel(appointmentId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(appointmentId))
    }

    /** Stable per appointment so a revision update replaces the existing alert instead of stacking. */
    private fun notificationId(appointmentId: String): Int = appointmentId.hashCode()

    private fun postIfPermitted(id: Int, notification: Notification): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted || !isReady()) return false
        return runCatching { NotificationManagerCompat.from(context).notify(id, notification); true }.getOrDefault(false)
    }

    companion object {
        const val EXTRA_APPOINTMENT = "guincho_appointment"
        const val EXTRA_PROVIDER = "guincho_provider"
        const val CHANNEL_ID = "pittec_incoming_requests"
    }
}
