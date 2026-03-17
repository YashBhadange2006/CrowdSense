package com.example.ble.userinterface.screen

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.ble.bluetooth.BleScanner
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.util.LocationHelper
import org.osmdroid.util.GeoPoint

// ── Colours ───────────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF0A0E14)
private val BgCard      = Color(0xFF111827)
private val DividerCol  = Color(0xFF1E2D3D)
private val AccentCyan  = Color(0xFF00E5FF)
private val TextPrimary = Color(0xFFE8F0FE)
private val TextMuted   = Color(0xFF6B7E99)
private val TextDim     = Color(0xFF3D5068)

private fun DensityLevel.userLabel() = when (this) {
    DensityLevel.LOW    -> "All Clear"
    DensityLevel.MEDIUM -> "Getting Busy"
    DensityLevel.HIGH   -> "Very Crowded"
    DensityLevel.DANGER -> "Dangerously Crowded"
}

private fun DensityLevel.userAdvice() = when (this) {
    DensityLevel.LOW    -> "Good time to travel. Platforms are clear."
    DensityLevel.MEDIUM -> "Moderate crowd nearby. Expect some delays."
    DensityLevel.HIGH   -> "Heavy crowding detected. Consider waiting."
    DensityLevel.DANGER -> "Dangerous density. Avoid this area now."
}

private fun DensityLevel.primaryColor() = when (this) {
    DensityLevel.LOW    -> Color(0xFF00FF9C)
    DensityLevel.MEDIUM -> Color(0xFFFFB800)
    DensityLevel.HIGH   -> Color(0xFFFF7A00)
    DensityLevel.DANGER -> Color(0xFFFF3B3B)
}

@Composable
fun HomeScreen(
    crowdScore          : CrowdScore?,
    devices             : List<BleDevice>,
    isScanning          : Boolean,
    onPermissionsGranted: () -> Unit,
) {
    val context = LocalContext.current

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) onPermissionsGranted()
    }



    LaunchedEffect(Unit) {
        if (!hasPermissions(context, permissions)) {
            permissionLauncher.launch(permissions)
        }
    }


    // Pulse animation for status circle
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue  = 0.92f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        // App name
        Text(
            text          = "YATRI",
            color         = TextPrimary,
            fontSize      = 13.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 6.sp
        )
        Text(
            text          = "crowd intelligence",
            color         = TextDim,
            fontSize      = 10.sp,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        // ── Big status circle ─────────────────────────────────────────────
        val level       = crowdScore?.level ?: DensityLevel.LOW
        val levelColor  = level.primaryColor()

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                levelColor.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // Main circle
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                levelColor.copy(alpha = 0.2f),
                                BgCard
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, levelColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (crowdScore == null) {
                        Text(
                            text       = "...",
                            color      = TextDim,
                            fontSize   = 32.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text      = "scanning",
                            color     = TextDim,
                            fontSize  = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            text       = level.userLabel().uppercase(),
                            color      = levelColor,
                            fontSize   = when (level) {
                                DensityLevel.LOW    -> 22.sp
                                DensityLevel.MEDIUM -> 16.sp
                                else                -> 14.sp
                            },
                            fontWeight    = FontWeight.Bold,
                            fontFamily    = FontFamily.Monospace,
                            textAlign     = TextAlign.Center,
                            letterSpacing = 1.sp,
                            modifier      = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text      = "${crowdScore.totalNearby} devices nearby",
                            color     = TextMuted,
                            fontSize  = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Advisory text ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(levelColor.copy(alpha = 0.08f))
                .border(1.dp, levelColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text      = if (crowdScore != null) level.userAdvice()
                else "Starting crowd detection...",
                color     = if (crowdScore != null) levelColor else TextDim,
                fontSize  = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Quick stats row ───────────────────────────────────────────────
        if (crowdScore != null) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickStatCard(
                    label  = "App Users",
                    value  = crowdScore.appUserCount.toString(),
                    color  = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    label  = "Anonymous",
                    value  = crowdScore.anonymousCount.toString(),
                    color  = TextMuted,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    label  = "Score",
                    value  = "%.1f".format(crowdScore.score),
                    color  = levelColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── Map ───────────────────────────────────────────────────────────
        Text(
            text          = "NEARBY DENSITY",
            color         = TextMuted,
            fontSize      = 10.sp,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 2.sp,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DividerCol, RoundedCornerShape(16.dp))
        ) {
            MapScreen(
                deviceCount  = devices.size,
                densityLevel = crowdScore?.level
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Scanning status ───────────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isScanning) AccentCyan.copy(alpha = pulseScale)
                        else TextDim,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text      = if (isScanning) "Live — sensing your surroundings"
                else "Tap to start sensing",
                color     = if (isScanning) AccentCyan else TextDim,
                fontSize  = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuickStatCard(
    label   : String,
    value   : String,
    color   : Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF1E2D3D), RoundedCornerShape(10.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = value,
                color      = color,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text      = label,
                color     = Color(0xFF6B7E99),
                fontSize  = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}