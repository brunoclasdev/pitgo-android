package com.trustarx.pitgo.core.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.trustarx.pitgo.core.map.model.MapCoordinate
import java.math.BigDecimal

enum class ExternalNavigator(val packageName: String) {
    WAZE("com.waze"),
    GOOGLE_MAPS("com.google.android.apps.maps"),
}

data class ExternalNavigationOption(val navigator: ExternalNavigator, val available: Boolean)

enum class ExternalNavigationLaunchResult { OPENED, UNAVAILABLE }

object ExternalNavigationLauncher {
    fun options(context: Context, destination: MapCoordinate): List<ExternalNavigationOption> =
        ExternalNavigator.entries.map { navigator ->
            ExternalNavigationOption(navigator, isAvailable(context, navigator, destination))
        }

    fun open(
        context: Context,
        navigator: ExternalNavigator,
        destination: MapCoordinate,
    ): ExternalNavigationLaunchResult {
        if (!isAvailable(context, navigator, destination)) return ExternalNavigationLaunchResult.UNAVAILABLE
        return try {
            context.startActivity(intent(navigator, destination))
            ExternalNavigationLaunchResult.OPENED
        } catch (_: ActivityNotFoundException) {
            ExternalNavigationLaunchResult.UNAVAILABLE
        } catch (_: SecurityException) {
            ExternalNavigationLaunchResult.UNAVAILABLE
        }
    }

    private fun isAvailable(context: Context, navigator: ExternalNavigator, destination: MapCoordinate): Boolean {
        if (!destination.isValid) return false
        return try {
            intent(navigator, destination).resolveActivity(context.packageManager) != null
        } catch (_: SecurityException) {
            false
        }
    }

    private fun intent(navigator: ExternalNavigator, destination: MapCoordinate): Intent {
        val point = "${BigDecimal.valueOf(destination.latitude).toPlainString()}," +
            BigDecimal.valueOf(destination.longitude).toPlainString()
        val uri = when (navigator) {
            ExternalNavigator.WAZE -> Uri.parse("waze://").buildUpon()
                .appendQueryParameter("ll", point)
                .appendQueryParameter("navigate", "yes")
                .build()
            ExternalNavigator.GOOGLE_MAPS -> Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                .appendQueryParameter("api", "1")
                .appendQueryParameter("destination", point)
                .appendQueryParameter("travelmode", "driving")
                .appendQueryParameter("dir_action", "navigate")
                .build()
        }
        return Intent(Intent.ACTION_VIEW, uri).setPackage(navigator.packageName)
    }
}
