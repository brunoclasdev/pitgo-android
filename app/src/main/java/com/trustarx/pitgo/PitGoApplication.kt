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
        MapLibre.getInstance(applicationContext)
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        // Public Key only — the Access Token never ships in the app (see mp-* Edge Functions).
        MercadoPagoSDK.initialize(this, BuildConfig.MERCADO_PAGO_PUBLIC_KEY, CountryCode.BRA)
        container = PitGoAppContainer(this)
    }
}
