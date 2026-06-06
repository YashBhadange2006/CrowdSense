package com.example.ble.userinterface.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.BleScanner
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.util.LocationHelper
import org.osmdroid.util.GeoPoint

// ── Colour tokens ─────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF0A0E14)
private val BgCard      = Color(0xFF111827)
private val BgCardAlt   = Color(0xFF0F1923)
private val DividerCol  = Color(0xFF1E2D3D)
private val AccentCyan  = Color(0xFF00E5FF)
private val AccentGreen = Color(0xFF00FF9C)
private val AccentAmber = Color(0xFFFFB800)
private val AccentRed   = Color(0xFFFF3B3B)
private val TextPrimary = Color(0xFFE8F0FE)
private val TextMuted   = Color(0xFF6B7E99)
private val TextDim     = Color(0xFF3D5068)

private fun DensityLevel.accentColor() = when (this) {
    DensityLevel.LOW    -> Color(0xFF00FF9C)
    DensityLevel.MEDIUM -> Color(0xFFFFB800)
    DensityLevel.HIGH   -> Color(0xFFFF7A00)
    DensityLevel.DANGER -> Color(0xFFFF3B3B)
}

private fun DensityLevel.label() = when (this) {
    DensityLevel.LOW    -> "LOW"
    DensityLevel.MEDIUM -> "MEDIUM"
    DensityLevel.HIGH   -> "HIGH"
    DensityLevel.DANGER -> "DANGER"
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ScanScreen(
    onPermissionsGranted : () -> Unit = {},
    externalIsScanning   : Boolean? = null,        // ← add
    onStartScan          : () -> Unit = {},         // ← add
    onStopScan           : () -> Unit = {},         // ← add
    externalDevices      : List<BleDevice>? = null, // ← add
    externalCrowdScore   : CrowdScore? = null       // ← add
){
    val context = LocalContext.current

    var localDevices    by remember { mutableStateOf(listOf<BleDevice>()) }
    var localCrowdScore by remember { mutableStateOf<CrowdScore?>(null) }
    var localIsScanning by remember { mutableStateOf(false) }

    var devices    = externalDevices    ?: localDevices
    var crowdScore = externalCrowdScore ?: localCrowdScore
    var isScanning = externalIsScanning ?: localIsScanning

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionsGranted()
            onStartScan()
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermissions(context, permissions)) onPermissionsGranted()
    }

    val scanner = remember {
        if (externalDevices == null) {
            BleScanner(
                context             = context,
                onDeviceFound       = { localDevices = it },
                onCrowdScoreUpdated = { localCrowdScore = it }
            )
        } else null
    }


    LaunchedEffect(Unit) {
        LocationHelper.getLastLocation(context) { lat, lon ->
            Log.d("Location", "Got fix: $lat, $lon")
            scanner?.currentLocation = GeoPoint(lat, lon)
        }
    }


    // Pulse animation for scanning dot
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Header ───────────────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text          = "CROWDSENSE",
                            color         = TextPrimary,
                            fontSize      = 22.sp,
                            fontWeight    = FontWeight.Bold,
                            fontFamily    = FontFamily.Monospace,
                            letterSpacing = 4.sp
                        )
                        Text(
                            text          = "density monitoring system",
                            color         = TextMuted,
                            fontSize      = 11.sp,
                            fontFamily    = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = AccentCyan.copy(alpha = pulseAlpha),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }
                HorizontalDivider(color = DividerCol, thickness = 1.dp)
            }

            // ── Scan toggle button ───────────────────────────────────────────
            item {
                val bgColor by animateColorAsState(
                    targetValue   = if (isScanning) Color(0xFF1A0A0A) else Color(0xFF0A1A12),
                    animationSpec = tween(400),
                    label         = "btnBg"
                )
                val borderColor by animateColorAsState(
                    targetValue   = if (isScanning) AccentRed else AccentGreen,
                    animationSpec = tween(400),
                    label         = "btnBorder"
                )

                Button(
                    onClick = {
                        if (hasPermissions(context, permissions)) {
                            if (isScanning) {
                                // Use service stop if available, else local scanner
                                if (externalIsScanning != null) {
                                    onStopScan()
                                } else {
                                    scanner?.stopScan()
                                    localIsScanning = false
                                }
                            } else {
                                // Use service start if available, else local scanner
                                if (externalIsScanning != null) {
                                    onStartScan()
                                } else {
                                    localDevices    = emptyList()
                                    localCrowdScore = null
                                    scanner?.startContinuousScan()
                                    localIsScanning = true
                                }
                            }
                        } else {
                            permissionLauncher.launch(permissions)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = bgColor),
                    shape  = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text          = if (isScanning) "■  STOP SCAN" else "▶  START SCAN",
                        color         = borderColor,
                        fontFamily    = FontFamily.Monospace,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize      = 14.sp
                    )
                }
            }

            // ── Crowd score card ─────────────────────────────────────────────
            item {
                SectionLabel("CROWD ANALYSIS")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(1.dp, DividerCol, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    if (crowdScore == null) {
                        Text(
                            text       = "Awaiting first scan cycle…",
                            color      = TextDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp
                        )
                    } else {
                        val s = crowdScore!!
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                            // Score + level badge
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text          = "DENSITY SCORE",
                                        color         = TextMuted,
                                        fontSize      = 10.sp,
                                        fontFamily    = FontFamily.Monospace,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text       = "%.2f".format(s.score),
                                        color      = TextPrimary,
                                        fontSize   = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                val levelColor = s.level.accentColor()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(levelColor.copy(alpha = 0.12f))
                                        .border(
                                            1.dp,
                                            levelColor.copy(alpha = 0.6f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text          = s.level.label(),
                                        color         = levelColor,
                                        fontFamily    = FontFamily.Monospace,
                                        fontWeight    = FontWeight.Bold,
                                        fontSize      = 14.sp,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = DividerCol)

                            // Device counts row
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricCell("APP USERS",  s.appUserCount.toString(),  AccentCyan)
                                MetricCell("ANONYMOUS",  s.anonymousCount.toString(), TextMuted)
                                MetricCell("TOTAL NEAR", s.totalNearby.toString(),    AccentCyan)
                            }

                            HorizontalDivider(color = DividerCol)

                            // Signal metrics row
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricCell(
                                    label  = "AVG RSSI",
                                    value  = "%.1f dBm".format(s.avgRssi),
                                    accent = if (s.avgRssi < -75f) AccentAmber else AccentGreen
                                )
                                MetricCell(
                                    label  = "CELL PRESSURE",
                                    value  = "%.2f".format(s.cellularPressure),
                                    accent = if (s.cellularPressure > 2f) AccentRed else AccentGreen
                                )
                                MetricCell(
                                    label  = "RSSI PENALTY",
                                    value  = if (s.avgRssi < -75f) "1.5×" else "1.0×",
                                    accent = if (s.avgRssi < -75f) AccentAmber else TextMuted
                                )
                            }

                            HorizontalDivider(color = DividerCol)

                            // Formula breakdown
                            val bleRaw  = (s.appUserCount * 3f) + (s.anonymousCount * 1f)
                            val penalty = if (s.avgRssi < -75f) 1.5f else 1.0f
                            val bleAdj  = bleRaw * penalty
                            val cellAdj = s.cellularPressure * 2f

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text          = "SCORE BREAKDOWN",
                                    color         = TextMuted,
                                    fontSize      = 10.sp,
                                    fontFamily    = FontFamily.Monospace,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text       = "  BLE raw  = (${s.appUserCount}×3) + (${s.anonymousCount}×1) = %.1f".format(bleRaw),
                                    color      = TextMuted,
                                    fontSize   = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text       = "  BLE adj  = %.1f × %.1f = %.2f".format(bleRaw, penalty, bleAdj),
                                    color      = TextMuted,
                                    fontSize   = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text       = "  Cell adj = %.2f × 2.0 = %.2f".format(s.cellularPressure, cellAdj),
                                    color      = TextMuted,
                                    fontSize   = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text       = "  TOTAL    = %.2f + %.2f = %.2f".format(bleAdj, cellAdj, s.score),
                                    color      = AccentCyan,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // ── Map — FIXED HEIGHT so it doesn't take over screen ────────────
            item {
                SectionLabel("LOCATION HEATMAP")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)           // ← fixed height, key fix
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DividerCol, RoundedCornerShape(12.dp))
                ) {
                    MapScreen(
                        deviceCount  = devices.size,
                        densityLevel = crowdScore?.level
                    )
                }
            }

            // ── Device debug list ────────────────────────────────────────────
            item {
                SectionLabel("RAW DEVICE LOG  [${devices.size}]")
            }

            if (devices.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = if (isScanning) "Listening for signals…" else "No scan started",
                            color      = TextDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp
                        )
                    }
                }
            } else {
                items(devices) { device ->
                    DeviceDebugCard(device)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ── Device debug card ─────────────────────────────────────────────────────────

@Composable
private fun DeviceDebugCard(device: BleDevice) {
    val isAppUser  = device.isAppUser
    val borderTint = if (isAppUser) AccentCyan.copy(alpha = 0.4f) else DividerCol

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCardAlt)
            .border(1.dp, borderTint, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Name + type badge
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = if (isAppUser && !device.name.startsWith("CrowdSense_"))
                        "${device.name} (CrowdSense)"
                    else device.name,
                    color      = if (isAppUser) AccentCyan else TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    modifier   = Modifier.weight(1f)
                )
                Text(
                    text          = if (isAppUser) "APP USER" else "ANON",
                    color         = if (isAppUser) AccentCyan else TextDim,
                    fontSize      = 10.sp,
                    fontFamily    = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text       = device.address,
                color      = TextDim,
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            HorizontalDivider(color = DividerCol)

            // Metrics
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DebugMetric("RSSI",   "${device.rssi} dBm")
                DebugMetric("DIST",   "%.2f m".format(device.distance))
                DebugMetric("WEIGHT", if (isAppUser) "3.0×" else "1.0×")
                DebugMetric(
                    "AGE",
                    "${(System.currentTimeMillis() - device.lastSeen) / 1000}s"
                )
            }

            // RSSI signal bar
            val fraction   = ((device.rssi + 100f) / 60f).coerceIn(0f, 1f)
            val barColor   = when {
                fraction > 0.6f -> AccentGreen
                fraction > 0.3f -> AccentAmber
                else            -> AccentRed
            }

            Column {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "SIGNAL STRENGTH",
                        color      = TextDim,
                        fontSize   = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "${(fraction * 100).toInt()}%",
                        color      = barColor,
                        fontSize   = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DividerCol)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(barColor.copy(alpha = 0.6f), barColor)
                                )
                            )
                    )
                }
            }
        }
    }
}

// ── Small reusable composables ────────────────────────────────────────────────

@Composable
private fun MetricCell(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text          = label,
            color         = TextMuted,
            fontSize      = 9.sp,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 1.sp,
            textAlign     = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text       = value,
            color      = accent,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center
        )
    }
}

@Composable
private fun DebugMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextDim,     fontSize = 9.sp,  fontFamily = FontFamily.Monospace)
        Text(value, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        color         = TextMuted,
        fontSize      = 10.sp,
        fontFamily    = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier      = Modifier.padding(bottom = 6.dp, top = 4.dp)
    )
}

// ── Permission helper ─────────────────────────────────────────────────────────

fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}