package com.example.safewalk.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Utility to launch external Google Maps (or compatible map application)
 * at the specified coordinates using Android ACTION_VIEW + geo URI.
 *
 * Requirements:
 * - Server/real runtime coordinates (never hardcoded)
 * - Safe resolution of intent (no crashes if Google Maps is not installed)
 * - Graceful fallback to generic map viewer, web maps, or toast message
 * - Zero Google Maps SDK or API key dependencies
 */
object GoogleMapsIntentLauncher {

    fun isValidCoordinates(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        if (latitude < -90.0 || latitude > 90.0) return false
        if (longitude < -180.0 || longitude > 180.0) return false
        return true
    }

    fun buildGeoUriString(latitude: Double, longitude: Double, label: String = "SafeWalk Requester"): String {
        val encodedLabel = try {
            java.net.URLEncoder.encode(label, "UTF-8")
        } catch (_: Exception) {
            label
        }
        return "geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)&z=16"
    }

    fun launch(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        label: String = "SafeWalk Requester"
    ): Boolean {
        if (!isValidCoordinates(latitude, longitude)) {
            try {
                Toast.makeText(context, "Location coordinates unavailable", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
            return false
        }

        val geoUriString = buildGeoUriString(latitude!!, longitude!!, label)
        val geoUri = Uri.parse(geoUriString)

        // 1. Try launching specifically with Google Maps package
        val googleMapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (googleMapsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(googleMapsIntent)
                return true
            }
        } catch (_: Exception) {
            // Fall through to generic handler
        }

        // 2. Fallback: Generic map application handling geo URI
        val genericMapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (genericMapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(genericMapIntent)
                return true
            }
        } catch (_: Exception) {
            // Fall through to browser URL
        }

        // 3. Fallback: Open in Web browser via Google Maps URL
        try {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            return true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Google Maps is not installed.", Toast.LENGTH_SHORT).show()
            return false
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open map: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
