package com.example.safewalk.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.safewalk.BuildConfig
import com.example.safewalk.domain.model.EmergencyRole
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.module.http.HttpRequestImpl
import java.util.concurrent.atomic.AtomicBoolean

private val mapLibreInitialized = AtomicBoolean(false)

private fun initMapLibreOnce(context: Context) {
    if (mapLibreInitialized.compareAndSet(false, true)) {
        try {
            MapLibre.getInstance(context)
            // Configure custom User-Agent for MapTiler API Key restriction compliance
            val customOkHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "SafeWalk-Android/1.0")
                        .build()
                    chain.proceed(req)
                }
                .build()
            HttpRequestImpl.setOkHttpClient(customOkHttpClient)
        } catch (_: Exception) {}
    }
}

/**
 * Interactive in-app map component powered by MapLibre Native and MapTiler Cloud.
 * Serves strictly as a passive visualization layer:
 * - Shows requester marker (Red SOS beacon)
 * - Shows current user marker (Blue locator)
 * - Shows independent responder markers (Role-colored pins)
 * - Updates dynamically on WebSocket real-time events without duplicate polling
 * - Honors user manual gestures (pan/zoom) and provides a "Focus Emergency" recenter action
 */
@Composable
fun SafeWalkMapView(
    requesterLocation: Location?,
    currentUserLocation: Location? = null,
    participants: List<ParticipantLocation> = emptyList(),
    modifier: Modifier = Modifier,
    styleUrl: String = "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildConfig.MAPTILER_API_KEY}"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    initMapLibreOnce(context)

    var maplibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    val activeMarkers = remember { mutableMapOf<String, Marker>() }
    var userManuallyMovedCamera by remember { mutableStateOf(false) }
    var mapStyleLoaded by remember { mutableStateOf(false) }

    var previousParticipantCount by remember { mutableStateOf(0) }
    var previousRequesterCoords by remember { mutableStateOf<String?>(null) }

    fun fitCameraToBounds(animated: Boolean = true) {
        val map = maplibreMapInstance ?: return
        val points = mutableListOf<LatLng>()

        if (requesterLocation != null && !(requesterLocation.latitude == 0.0 && requesterLocation.longitude == 0.0)) {
            points.add(LatLng(requesterLocation.latitude, requesterLocation.longitude))
        }
        if (currentUserLocation != null && !(currentUserLocation.latitude == 0.0 && currentUserLocation.longitude == 0.0)) {
            points.add(LatLng(currentUserLocation.latitude, currentUserLocation.longitude))
        }
        participants.forEach { p ->
            if (!(p.location.latitude == 0.0 && p.location.longitude == 0.0)) {
                points.add(LatLng(p.location.latitude, p.location.longitude))
            }
        }

        if (points.isEmpty()) return

        try {
            if (points.size == 1) {
                val update = CameraUpdateFactory.newLatLngZoom(points.first(), 15.2)
                if (animated) map.animateCamera(update, 700) else map.moveCamera(update)
            } else {
                var minLat = Double.MAX_VALUE
                var maxLat = -Double.MAX_VALUE
                var minLng = Double.MAX_VALUE
                var maxLng = -Double.MAX_VALUE

                points.forEach { pt ->
                    if (pt.latitude < minLat) minLat = pt.latitude
                    if (pt.latitude > maxLat) maxLat = pt.latitude
                    if (pt.longitude < minLng) minLng = pt.longitude
                    if (pt.longitude > maxLng) maxLng = pt.longitude
                }

                val centerLat = (minLat + maxLat) / 2.0
                val centerLng = (minLng + maxLng) / 2.0
                val center = LatLng(centerLat, centerLng)

                val latSpan = maxLat - minLat
                val lngSpan = maxLng - minLng
                val maxSpan = maxOf(latSpan, lngSpan)

                val zoom = when {
                    maxSpan < 0.0015 -> 15.5
                    maxSpan < 0.0040 -> 14.5
                    maxSpan < 0.0080 -> 13.5
                    maxSpan < 0.0200 -> 12.5
                    maxSpan < 0.0500 -> 11.5
                    maxSpan < 0.1500 -> 10.5
                    maxSpan < 0.4000 -> 9.0
                    maxSpan < 1.0000 -> 8.0
                    maxSpan < 2.5000 -> 7.0
                    maxSpan < 6.0000 -> 5.5
                    else -> 4.5
                }

                val update = CameraUpdateFactory.newLatLngZoom(center, zoom)
                if (animated) map.animateCamera(update, 700) else map.moveCamera(update)
            }
        } catch (e: Exception) {
            android.util.Log.e("SafeWalkMap", "Camera fit error: ${e.message}", e)
        }
    }

    // Synchronize markers when locations or participants change
    LaunchedEffect(requesterLocation, currentUserLocation, participants, mapStyleLoaded) {
        val map = maplibreMapInstance ?: return@LaunchedEffect
        if (!mapStyleLoaded) return@LaunchedEffect

        val iconFactory = IconFactory.getInstance(context)
        val activeKeys = mutableSetOf<String>()

        // 1. Requester Marker
        if (requesterLocation != null && !(requesterLocation.latitude == 0.0 && requesterLocation.longitude == 0.0)) {
            val key = "REQUESTER"
            activeKeys.add(key)
            val pos = LatLng(requesterLocation.latitude, requesterLocation.longitude)
            val existing = activeMarkers[key]
            if (existing != null) {
                existing.position = pos
            } else {
                val icon = iconFactory.fromBitmap(MapMarkerBitmapHelper.createRequesterMarker(context))
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .icon(icon)
                        .title("Requester (SOS)")
                        .snippet(requesterLocation.formattedCoordinates)
                )
                activeMarkers[key] = marker
            }
        }

        // 2. Current User Marker (if separate from requester)
        if (currentUserLocation != null && !(currentUserLocation.latitude == 0.0 && currentUserLocation.longitude == 0.0)) {
            val key = "CURRENT_USER"
            activeKeys.add(key)
            val pos = LatLng(currentUserLocation.latitude, currentUserLocation.longitude)
            val existing = activeMarkers[key]
            if (existing != null) {
                existing.position = pos
            } else {
                val icon = iconFactory.fromBitmap(MapMarkerBitmapHelper.createCurrentUserMarker(context))
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .icon(icon)
                        .title("Your Location")
                        .snippet(currentUserLocation.formattedCoordinates)
                )
                activeMarkers[key] = marker
            }
        }

        // 3. Responders Markers (Multiple independent responders)
        participants.forEach { participant ->
            if (participant.role == EmergencyRole.RESPONDER &&
                !(participant.location.latitude == 0.0 && participant.location.longitude == 0.0)) {
                val key = "RESPONDER_${participant.userId}"
                activeKeys.add(key)
                val pos = LatLng(participant.location.latitude, participant.location.longitude)
                val existing = activeMarkers[key]
                if (existing != null) {
                    existing.position = pos
                    existing.snippet = participant.formattedDistance
                } else {
                    val initial = participant.userName.ifBlank { participant.safeWalkId }.take(1)
                    val icon = iconFactory.fromBitmap(
                        MapMarkerBitmapHelper.createResponderMarker(
                            context = context,
                            role = participant.role,
                            initial = if (initial.isNotBlank()) initial else "R",
                            userName = participant.userName
                        )
                    )
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(pos)
                            .icon(icon)
                            .title(participant.userName.ifBlank { "Responder" })
                            .snippet("${participant.role.displayName} • ${participant.formattedDistance}")
                    )
                    activeMarkers[key] = marker
                }
            }
        }

        // 4. Remove stale markers that are no longer active
        val toRemove = activeMarkers.keys - activeKeys
        toRemove.forEach { k ->
            activeMarkers.remove(k)?.let { map.removeMarker(it) }
        }

        // 5. Update camera if emergency moved, participant joined, or user hasn't manually panned
        val curReqCoords = requesterLocation?.formattedCoordinates
        val reqCoordsChanged = curReqCoords != null && curReqCoords != previousRequesterCoords
        val countChanged = participants.size != previousParticipantCount

        if (reqCoordsChanged || countChanged) {
            userManuallyMovedCamera = false
            previousParticipantCount = participants.size
            previousRequesterCoords = curReqCoords
            fitCameraToBounds(animated = true)
        } else if (!userManuallyMovedCamera) {
            fitCameraToBounds(animated = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    onCreate(null)
                    onStart()
                    onResume()
                    mapViewInstance = this
                    getMapAsync { map ->
                        maplibreMapInstance = map
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isCompassEnabled = true

                        // Detect user gestures so we don't snap camera against user will
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                userManuallyMovedCamera = true
                            }
                        }

                        map.setStyle(styleUrl) {
                            mapStyleLoaded = true
                            fitCameraToBounds(animated = false)
                        }
                    }
                }
            },
            update = {
                // Update bindings if needed
            }
        )

        // Floating Action Button: "Focus / Recenter Emergency"
        FloatingActionButton(
            onClick = {
                userManuallyMovedCamera = false
                fitCameraToBounds(animated = true)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(40.dp),
            shape = CircleShape,
            containerColor = Color.White.copy(alpha = 0.92f),
            contentColor = SafeWalkBlueSecondary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
        ) {
            Text(
                text = "🎯",
                fontSize = 18.sp
            )
        }
    }

    // Lifecycle observer for proper MapView cleanup
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewInstance ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mv.onStart()
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                Lifecycle.Event.ON_STOP -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    activeMarkers.clear()
                    mv.onDestroy()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeMarkers.clear()
            mapViewInstance?.onDestroy()
        }
    }
}
