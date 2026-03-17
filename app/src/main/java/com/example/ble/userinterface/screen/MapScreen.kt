package com.example.ble.userinterface.screen

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ble.FirebaseReader
import com.example.ble.RemoteCrowdPoint
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.util.LocationHelper
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

@Composable
fun MapScreen(
    deviceCount : Int,
    densityLevel: DensityLevel? = null
) {
    val context  = LocalContext.current
    var location by remember { mutableStateOf<GeoPoint?>(null) }
    var remotePoints by remember { mutableStateOf<List<RemoteCrowdPoint>>(emptyList()) }

    // Get own location
    LaunchedEffect(Unit) {
        LocationHelper.getLastLocation(context) { lat, lon ->
            location = GeoPoint(lat, lon)
        }
    }

    // Listen to ALL crowd points from Firebase
    LaunchedEffect(Unit) {
        FirebaseReader.listenToLatest { points ->
            remotePoints = points
        }
    }

    // Stop listener when composable leaves screen
    DisposableEffect(Unit) {
        onDispose {
            FirebaseReader.stopListening()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF0A0E14))
    ) {

        val mapViewRef = remember { mutableStateOf<MapView?>(null) }

        location?.let { myLocation ->
            AndroidView(
                factory = { ctx ->
                    createMapView(ctx, myLocation, deviceCount, densityLevel).also {
                        mapViewRef.value = it
                    }
                },
                update = { mapView ->
                    updateMap(
                        mapView      = mapView,
                        myLocation   = myLocation,
                        deviceCount  = deviceCount,
                        densityLevel = densityLevel,
                        remotePoints = remotePoints  // always latest value
                    )
                }
            )

            LaunchedEffect(remotePoints) {
                mapViewRef.value?.let { mapView ->
                    updateMap(
                        mapView      = mapView,
                        myLocation   = myLocation,
                        deviceCount  = deviceCount,
                        densityLevel = densityLevel,
                        remotePoints = remotePoints
                    )
                }
            }
        }

        if (location == null) {
            Text(
                text       = "Resolving location…",
                color      = androidx.compose.ui.graphics.Color(0xFF3D5068),
                fontFamily = FontFamily.Monospace,
                fontSize   = TextUnit(12f, TextUnitType.Sp),
                modifier   = Modifier.align(Alignment.Center)
            )
        }

        // Live indicator — top right
        if (remotePoints.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            androidx.compose.ui.graphics.Color(0xFF00E5FF),
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
                Text(
                    text      = "${remotePoints.size} active zones",
                    color     = androidx.compose.ui.graphics.Color(0xFF00E5FF),
                    fontSize  = TextUnit(9f, TextUnitType.Sp),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ── Map creation ──────────────────────────────────────────────────────────────

private fun createMapView(
    context     : Context,
    startPoint  : GeoPoint,
    deviceCount : Int,
    densityLevel: DensityLevel?
): MapView {
    Configuration.getInstance().load(
        context,
        context.getSharedPreferences("osm", Context.MODE_PRIVATE)
    )

    val mapView = MapView(context)
    mapView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    mapView.setMultiTouchControls(true)
    mapView.controller.setZoom(17.0)
    mapView.controller.setCenter(startPoint)

    // Dark tile filter
    mapView.overlayManager.tilesOverlay.setColorFilter(
        android.graphics.ColorMatrixColorFilter(
            floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                0f, -1f,  0f, 0f, 255f,
                0f,  0f, -1f, 0f, 255f,
                0f,  0f,  0f, 1f,   0f
            )
        )
    )

    return mapView
}

// ── Map update ────────────────────────────────────────────────────────────────

private fun updateMap(
    mapView     : MapView,
    myLocation  : GeoPoint,
    deviceCount : Int,
    densityLevel: DensityLevel?,
    remotePoints: List<RemoteCrowdPoint>
) {
    mapView.overlays.clear()

    // Draw all remote crowd points from Firebase first (bottom layer)
    remotePoints.forEach { point ->
        val geoPoint = GeoPoint(point.lat, point.lng)
        val level    = parseDensityLevel(point.level)
        drawSpreadingBlob(mapView, geoPoint, point.score, level)
    }

    // Draw own location on top (top layer)
    drawSpreadingBlob(mapView, myLocation, null, densityLevel)
    drawMyLocationDot(mapView, myLocation)

    mapView.invalidate()
}

// ── Spreading blob — the key visual effect ────────────────────────────────────

/**
 * Draws multiple concentric circles with decreasing opacity.
 * Creates a natural ink-spread / heat-blob effect.
 * When two blobs overlap, their transparent layers visually merge
 * making the overlapping area appear brighter/more intense —
 * exactly like mixing two colours.
 *
 * Score drives the radius — higher crowd = larger spread.
 * This means two HIGH areas next to each other naturally
 * converge into one large danger zone visually.
 */
private fun drawSpreadingBlob(
    mapView : MapView,
    center  : GeoPoint,
    score   : Float?,
    level   : DensityLevel?
) {
    val radius = when (level) {
        DensityLevel.LOW    -> 18.0
        DensityLevel.MEDIUM -> 28.0
        DensityLevel.HIGH   -> 38.0
        DensityLevel.DANGER -> 50.0
        null                -> 20.0
    }

    val (r, g, b) = when (level) {
        DensityLevel.LOW    -> Triple(0,   255, 156)
        DensityLevel.MEDIUM -> Triple(255, 184,   0)
        DensityLevel.HIGH   -> Triple(255, 122,   0)
        DensityLevel.DANGER -> Triple(255,  59,  59)
        null                -> Triple(0,   229, 255)
    }

    // Single circle — clean, doesn't cover map
    val circle = Polygon().apply {
        points      = Polygon.pointsAsCircle(center, radius)
        fillColor   = Color.argb(80, r, g, b)   // low alpha = map visible through it
        strokeColor = Color.argb(180, r, g, b)  // stronger border = clear boundary
        strokeWidth = 2f
    }

    mapView.overlays.add(circle)
}

// ── My location dot — small precise indicator ─────────────────────────────────

private fun drawMyLocationDot(mapView: MapView, center: GeoPoint) {
    // Outer white ring
    val ring = Polygon().apply {
        points      = Polygon.pointsAsCircle(center, 6.0)
        fillColor   = Color.argb(255, 255, 255, 255)
        strokeColor = Color.TRANSPARENT
        strokeWidth = 0f
    }
    // Inner cyan dot
    val dot = Polygon().apply {
        points      = Polygon.pointsAsCircle(center, 3.5)
        fillColor   = Color.argb(255, 0, 229, 255)
        strokeColor = Color.TRANSPARENT
        strokeWidth = 0f
    }
    mapView.overlays.add(ring)
    mapView.overlays.add(dot)
}

// ── Helper ────────────────────────────────────────────────────────────────────

private fun parseDensityLevel(level: String): DensityLevel {
    return try {
        DensityLevel.valueOf(level)
    } catch (e: Exception) {
        DensityLevel.LOW
    }
}
