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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.BleScanner
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.ui.theme.BLETheme
import com.example.ble.util.LocationHelper
import org.osmdroid.util.GeoPoint
import kotlin.String

@Composable
private fun BgCard() = MaterialTheme.colorScheme.surface
@Composable
private fun BgCardAlt() = MaterialTheme.colorScheme.surfaceVariant
@Composable
private fun DividerCol() = MaterialTheme.colorScheme.outlineVariant
@Composable
private fun AccentCyan() = MaterialTheme.colorScheme.primary
@Composable
private fun AccentGreen() = MaterialTheme.colorScheme.secondary
@Composable
private fun AccentAmber() = MaterialTheme.colorScheme.tertiary
@Composable
private fun AccentRed() = MaterialTheme.colorScheme.error
@Composable
private fun TextPrimary() = MaterialTheme.colorScheme.onSurface
@Composable
private fun TextMuted() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable
private fun TextDim() = MaterialTheme.colorScheme.outline

@Composable
private fun DensityLevel.accentColor() = when (this) {
    DensityLevel.LOW    -> MaterialTheme.colorScheme.primary
    DensityLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
    DensityLevel.HIGH   -> MaterialTheme.colorScheme.tertiary
    DensityLevel.DANGER -> MaterialTheme.colorScheme.error
}

private fun DensityLevel.label() = when (this) {
    DensityLevel.LOW    -> "LOW"
    DensityLevel.MEDIUM -> "MEDIUM"
    DensityLevel.HIGH   -> "HIGH"
    DensityLevel.DANGER -> "DANGER"
}

@Composable
fun ScanScreen(
    onPermissionsGranted : () -> Unit = {},
    externalIsScanning   : Boolean? = null,        // add
    onStartScan          : () -> Unit = {},         // add
    onStopScan           : () -> Unit = {},         // add
    externalDevices      : List<BleDevice>? = null, // add
    externalCrowdScore   : CrowdScore? = null       // add
){
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
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
            .background(colorScheme.background)
    ) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

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
                            text          = "Crowd Analysis",
                            color         = TextPrimary(),
                            fontSize      = 22.sp,
                            fontWeight    = FontWeight.Bold,
                            fontFamily    = FontFamily.Default,
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            text          = "density monitoring system",
                            color         = TextMuted(),
                            fontSize      = 11.sp,
                            fontFamily    = FontFamily.Default,
                        )
                    }
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = AccentCyan().copy(alpha = pulseAlpha),
                                    shape = CircleShape
                                )
                        )
                    }
                }
                HorizontalDivider(color = DividerCol(), thickness = 1.dp)
            }

            item {
                val dynamicGradientBrush = remember(isScanning) {
                    if (isScanning) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF990000),
                                Color(0xFFD32F2F)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFD35400),
                                Color(0xFFE67E22)
                            )
                        )
                    }
                }

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
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    shape  = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 7.dp,
                        pressedElevation = 3.dp
                    )

                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(dynamicGradientBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if(isScanning) Icons.Filled.Stop else Icons.Filled.CellTower,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isScanning) "STOP SCAN" else "START SCAN",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Crowd Analysis",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard()),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    if (crowdScore == null) {
                        Text(
                            text       = "Awaiting first scan cycle…",
                            color      = TextDim(),
                            fontFamily = FontFamily.Default,
                            fontSize   = 13.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier
                                .padding(5.dp)
                        )
                    } else {
                        val s = crowdScore!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                            ) {

                            // Score + level badge
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text          = "DENSITY SCORE",
                                        color         = TextMuted(),
                                        fontSize      = 10.sp,
                                        fontFamily    = FontFamily.Default,
                                        style = MaterialTheme.typography.labelSmall,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text       = "%.2f".format(s.score),
                                        color      = TextPrimary(),
                                        fontSize   = 36.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Default
                                    )
                                }
                                val levelColor = s.level.accentColor()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(levelColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text          = s.level.label(),
                                        color         = levelColor,
                                        fontFamily    = FontFamily.Default,
                                        fontWeight    = FontWeight.Bold,
                                        fontSize      = 14.sp,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = DividerCol())

                            // Device counts row
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricCell("APP USERS",  s.appUserCount.toString(),  AccentCyan())
                                MetricCell("ANONYMOUS",  s.anonymousCount.toString(), TextMuted())
                                MetricCell("TOTAL NEAR", s.totalNearby.toString(),    AccentCyan())
                            }

                            HorizontalDivider(color = DividerCol())

                            // Signal metrics row
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricCell(
                                    label  = "AVG RSSI",
                                    value  = "%.1f dBm".format(s.avgRssi),
                                    accent = if (s.avgRssi < -75f) AccentAmber() else AccentGreen()
                                )
                                MetricCell(
                                    label  = "CELL PRESSURE",
                                    value  = "%.2f".format(s.cellularPressure),
                                    accent = if (s.cellularPressure > 2f) AccentRed() else AccentGreen()
                                )
                                MetricCell(
                                    label  = "RSSI PENALTY",
                                    value  = if (s.avgRssi >= -60f){"1.4×"} else if(s.avgRssi >= -68f) "1.2×" else "1.0×",
                                    accent = if (s.avgRssi >= -60f) AccentAmber() else if(s.avgRssi >= -68f) TextMuted() else TextMuted()
                                )
                            }

                            HorizontalDivider(color = DividerCol())

                            // Formula breakdown
                            val bleRaw  = (s.appUserCount * 3f) + (s.anonymousCount * 1f)
                            val penalty = if (s.avgRssi >= -60f) 1.4f else if(s.avgRssi >= -68f) 1.2f else 1.0f
                            val bleAdj  = bleRaw * penalty
                            val cellAdj = s.cellularPressure * 2f

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text          = "SCORE BREAKDOWN",
                                    color         = TextMuted(),
                                    fontSize      = 10.sp,
                                    fontFamily    = FontFamily.Default,
                                )
                                Text(
                                    text       = "BLE raw = (${s.appUserCount}×2.5) + (${s.anonymousCount}×1) = %.1f".format(bleRaw),
                                    color      = TextMuted(),
                                    fontSize   = 11.sp,
                                    fontFamily = FontFamily.Default
                                )
                                Text(
                                    text       = "BLE adj = %.1f × %.1f = %.2f".format(bleRaw, penalty, bleAdj),
                                    color      = TextMuted(),
                                    fontSize   = 11.sp,
                                    fontFamily = FontFamily.Default
                                )
                                Text(
                                    text       = "Cell adj = %.2f × 2.0 = %.2f".format(s.cellularPressure, cellAdj),
                                    color      = TextMuted(),
                                    fontSize   = 11.sp,
                                    fontFamily = FontFamily.Default
                                )
                                Text(
                                    text       = "TOTAL = %.2f + %.2f = %.2f".format(bleAdj, cellAdj, s.score),
                                    color      = AccentCyan(),
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Default
                                )
                            }
                        }
                    }
                }
            }

            // // Map - fixed height so it does not take over screen
            item {
                SectionLabel("LOCATION HEATMAP")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)           // fixed height, key fix
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DividerCol(), RoundedCornerShape(12.dp))
                ) {
                    MapScreen(
                        deviceCount  = devices.size,
                        densityLevel = crowdScore?.level
                    )
                }
            }

            // // Device debug list
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
                            color      = TextDim(),
                            fontFamily = FontFamily.Default,
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

@Composable
private fun DeviceDebugCard(device: BleDevice) {
    val isAppUser  = device.isAppUser
    val borderTint = if (isAppUser) AccentCyan().copy(alpha = 0.4f) else DividerCol()

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard()),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
        ) {

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
                    color      = if (isAppUser) AccentCyan() else TextPrimary(),
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    modifier   = Modifier.weight(1f)
                )
                Text(
                    text          = if (isAppUser) "APP USER" else "ANON",
                    color         = if (isAppUser) AccentCyan() else TextDim(),
                    fontSize      = 10.sp,
                    fontFamily    = FontFamily.Default,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text       = device.address,
                color      = TextDim(),
                fontSize   = 11.sp,
                fontFamily = FontFamily.Default
            )

            HorizontalDivider(color = DividerCol())

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
                fraction > 0.6f -> AccentGreen()
                fraction > 0.3f -> AccentAmber()
                else            -> AccentRed()
            }

            Column {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "SIGNAL STRENGTH",
                        color      = TextDim(),
                        fontSize   = 9.sp,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        "${(fraction * 100).toInt()}%",
                        color      = barColor,
                        fontSize   = 9.sp,
                        fontFamily = FontFamily.Default
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DividerCol())
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

@Composable
private fun MetricCell(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text          = label,
            color         = TextMuted(),
            fontSize      = 9.sp,
            fontFamily    = FontFamily.Default,
            letterSpacing = 1.sp,
            textAlign     = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text       = value,
            color      = accent,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Default,
            textAlign  = TextAlign.Center
        )
    }
}

@Composable
private fun DebugMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextDim(),     fontSize = 9.sp,  fontFamily = FontFamily.Default, letterSpacing = 1.sp)
        Text(value, color = TextPrimary(), fontSize = 11.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,letterSpacing = 1.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        color         = TextMuted(),
        fontSize      = 10.sp,
        fontFamily    = FontFamily.Default,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(bottom = 6.dp, top = 4.dp)
    )
}

fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

@Preview(name = "Full Dashboard Screen",showBackground = true)
@Composable
fun PreviewDevScanContent() {

    BLETheme(darkTheme = false) {
        ScanScreen(
            onPermissionsGranted = { },
            externalIsScanning   = true,
            onStartScan          = {},
            onStopScan           = {},
            externalDevices      = listOf(),
            externalCrowdScore   = CrowdScore(
                score = 50f,
                appUserCount = 2,
                anonymousCount = 3,
                totalNearby = 5,
                avgRssi = 25f,
                cellularPressure = 30f,
                level = DensityLevel.DANGER
            )
        )
    }
}

@Preview(name = "Single Log Card Row Component",showBackground = true)
@Composable
fun PreviewDebugLogCard() {
    BLETheme(darkTheme = false) {
        DeviceDebugCard(
            device = BleDevice(
                name = "Samsung",
                address = "AA:BB:CC:DD:EE:FF",
                rssi = -65,
                distance = 2.5,
                lastSeen = System.currentTimeMillis(),
                isAppUser = false
            )
        )
    }
}


