package com.example.ble.userinterface.screen

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.util.LocationHelper
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

@Composable
fun MapScreen(
    deviceCount: Int,
    densityLevel: DensityLevel? = null   // now accepts density level for better colouring
) {
    val context = LocalContext.current
    var location by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(Unit) {
        LocationHelper.getLastLocation(context) { lat, lon ->
            location = GeoPoint(lat, lon)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF0A0E14))
    ) {
        location?.let { geoPoint ->
            AndroidView(
                factory = {
                    createMapView(context, geoPoint, deviceCount, densityLevel)
                },
                update = { mapView ->
                    updateHeatCircle(mapView, geoPoint, deviceCount, densityLevel)
                }
            )
        }

        // Show placeholder text if location not yet resolved
        if (location == null) {
            androidx.compose.material3.Text(
                text = "Resolving location…",
                color = androidx.compose.ui.graphics.Color(0xFF3D5068),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = androidx.compose.ui.unit.TextUnit(
                    12f,
                    androidx.compose.ui.unit.TextUnitType.Sp
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun createMapView(
    context: Context,
    startPoint: GeoPoint,
    deviceCount: Int,
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

    // Dark tile style — matches app theme
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

    addHeatCircle(mapView, startPoint, deviceCount, densityLevel)
    return mapView
}

private fun updateHeatCircle(
    mapView: MapView,
    center: GeoPoint,
    deviceCount: Int,
    densityLevel: DensityLevel?
) {
    mapView.overlays.clear()
    addHeatCircle(mapView, center, deviceCount, densityLevel)
    mapView.invalidate()
}

private fun addHeatCircle(
    mapView: MapView,
    center: GeoPoint,
    deviceCount: Int,
    densityLevel: DensityLevel?
) {
    // Radius based on device count
    val radius = when {
        deviceCount < 5  -> 20.0
        deviceCount < 15 -> 40.0
        else             -> 60.0
    }

    // Use DensityLevel for colour if available, fallback to device count
    val col = when (densityLevel) {
        DensityLevel.LOW    -> Color.argb(80,  0,  255, 156)   // cyan-green
        DensityLevel.MEDIUM -> Color.argb(100, 255, 184,  0)   // amber
        DensityLevel.HIGH   -> Color.argb(120, 255, 122,  0)   // orange
        DensityLevel.DANGER -> Color.argb(140, 255,  59, 59)   // red
        null -> when {
            deviceCount < 5  -> Color.argb(80,  0,  255, 156)
            deviceCount < 15 -> Color.argb(100, 255, 184,  0)
            else             -> Color.argb(140, 255,  59, 59)
        }
    }

    val outlineColor = when (densityLevel) {
        DensityLevel.LOW    -> Color.argb(180,  0,  255, 156)
        DensityLevel.MEDIUM -> Color.argb(180, 255, 184,   0)
        DensityLevel.HIGH   -> Color.argb(180, 255, 122,   0)
        DensityLevel.DANGER -> Color.argb(200, 255,  59,  59)
        null                -> Color.argb(180, 100, 100, 100)
    }

    val circle = Polygon().apply {
        points      = Polygon.pointsAsCircle(center, radius)
        fillColor   = col
        strokeColor = outlineColor
        strokeWidth = 2f
    }

    mapView.overlays.add(circle)
}