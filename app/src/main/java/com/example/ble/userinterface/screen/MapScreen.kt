package com.example.ble.userinterface.screen

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
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
    deviceCount: Int,
    densityLevel: DensityLevel? = null
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var location by remember { mutableStateOf<GeoPoint?>(null) }
    var remotePoints by remember { mutableStateOf<List<RemoteCrowdPoint>>(emptyList()) }

    LaunchedEffect(Unit) {
        LocationHelper.getLastLocation(context) { lat, lon ->
            location = GeoPoint(lat, lon)
        }
    }

    LaunchedEffect(Unit) {
        FirebaseReader.listenToLatest { points ->
            remotePoints = points
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            FirebaseReader.stopListening()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
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
                        mapView = mapView,
                        myLocation = myLocation,
                        deviceCount = deviceCount,
                        densityLevel = densityLevel,
                        remotePoints = remotePoints,
                        colors = colors
                    )
                }
            )

            LaunchedEffect(remotePoints) {
                mapViewRef.value?.let { mapView ->
                    updateMap(
                        mapView = mapView,
                        myLocation = myLocation,
                        deviceCount = deviceCount,
                        densityLevel = densityLevel,
                        remotePoints = remotePoints,
                        colors = colors
                    )
                }
            }
        }

        if (location == null) {
            Text(
                text = "Resolving location…",
                color = colors.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = TextUnit(12f, TextUnitType.Sp),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (remotePoints.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(colors.primary, CircleShape)
                )
                Text(
                    text = "${remotePoints.size} active zones",
                    color = colors.primary,
                    fontSize = TextUnit(9f, TextUnitType.Sp),
                    fontFamily = FontFamily.Monospace
                )
            }
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

    mapView.overlayManager.tilesOverlay.setColorFilter(
        ColorMatrixColorFilter(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    return mapView
}

private fun updateMap(
    mapView: MapView,
    myLocation: GeoPoint,
    deviceCount: Int,
    densityLevel: DensityLevel?,
    remotePoints: List<RemoteCrowdPoint>,
    colors: androidx.compose.material3.ColorScheme
) {
    mapView.overlays.clear()

    remotePoints.forEach { point ->
        val geoPoint = GeoPoint(point.lat, point.lng)
        val level = parseDensityLevel(point.level)
        drawSpreadingBlob(mapView, geoPoint, point.score, level, colors)
    }

    drawSpreadingBlob(mapView, myLocation, null, densityLevel, colors)
    drawMyLocationDot(mapView, myLocation, colors)

    mapView.invalidate()
}

private fun drawSpreadingBlob(
    mapView: MapView,
    center: GeoPoint,
    score: Float?,
    level: DensityLevel?,
    colors: androidx.compose.material3.ColorScheme
) {
    val radius = when (level) {
        DensityLevel.LOW -> 8.0
        DensityLevel.MEDIUM -> 15.0
        DensityLevel.HIGH -> 30.0
        DensityLevel.DANGER -> 45.0
        null -> 20.0
    }

    val color = when (level) {
        DensityLevel.LOW -> colors.primary
        DensityLevel.MEDIUM -> colors.secondary
        DensityLevel.HIGH -> colors.tertiary
        DensityLevel.DANGER -> colors.error
        null -> colors.primary
    }

    val circle = Polygon().apply {
        points = Polygon.pointsAsCircle(center, radius)
        fillColor = Color.argb(80, Color.red(color.toArgb()), Color.green(color.toArgb()), Color.blue(color.toArgb()))
        strokeColor = Color.argb(180, Color.red(color.toArgb()), Color.green(color.toArgb()), Color.blue(color.toArgb()))
        strokeWidth = 2f
    }

    mapView.overlays.add(circle)
}

private fun drawMyLocationDot(
    mapView: MapView,
    center: GeoPoint,
    colors: androidx.compose.material3.ColorScheme
) {
    val ring = Polygon().apply {
        points = Polygon.pointsAsCircle(center, 6.0)
        fillColor = Color.argb(255, Color.red(colors.onSurface.toArgb()), Color.green(colors.onSurface.toArgb()), Color.blue(colors.onSurface.toArgb()))
        strokeColor = Color.TRANSPARENT
        strokeWidth = 0f
    }
    val dot = Polygon().apply {
        points = Polygon.pointsAsCircle(center, 3.5)
        fillColor = Color.argb(255, Color.red(colors.primary.toArgb()), Color.green(colors.primary.toArgb()), Color.blue(colors.primary.toArgb()))
        strokeColor = Color.TRANSPARENT
        strokeWidth = 0f
    }
    mapView.overlays.add(ring)
    mapView.overlays.add(dot)
}

private fun parseDensityLevel(level: String): DensityLevel = when (level.uppercase()) {
    "MEDIUM" -> DensityLevel.MEDIUM
    "HIGH" -> DensityLevel.HIGH
    "DANGER" -> DensityLevel.DANGER
    else -> DensityLevel.LOW
}

@Preview
@Composable
fun PreviewMapScreen() {
    MapScreen(deviceCount = 3, densityLevel = DensityLevel.MEDIUM)
}
