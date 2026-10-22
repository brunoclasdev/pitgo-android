package com.trustarx.pitgo

import android.app.Application
import com.mercadopago.sdk.android.domain.model.CountryCode
import com.mercadopago.sdk.android.initializer.MercadoPagoSDK
import com.trustarx.pitgo.core.app.PitGoAppContainer
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class PitGoApplication : Application() {
    lateinit var container: PitGoAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Remove notices left by versions that used system notifications for inspection review.
        getSystemService(android.app.NotificationManager::class.java).let { notifications ->
            notifications.activeNotifications.filter { it.tag?.startsWith("inspection-review:") == true }
                .forEach { notifications.cancel(it.tag, it.id) }
        }
        MapLibre.getInstance(applicationContext)
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        // Public Key only — the Access Token never ships in the app (see mp-* Edge Functions).
        if (!com.trustarx.pitgo.core.environment.EnvironmentConfig.simulated) {
            MercadoPagoSDK.initialize(this, BuildConfig.MERCADO_PAGO_PUBLIC_KEY, CountryCode.BRA)
        }
        container = PitGoAppContainer(this)
    }
}
