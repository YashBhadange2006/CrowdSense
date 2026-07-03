package com.example.ble.userinterface.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.ui.theme.BLETheme
import org.osmdroid.util.GeoPoint

private fun DensityLevel.userLabel() = when (this) {
    DensityLevel.LOW -> "All Clear"
    DensityLevel.MEDIUM -> "Getting Busy"
    DensityLevel.HIGH -> "Very Crowded"
    DensityLevel.DANGER -> "Dangerously Crowded"
}

private fun DensityLevel.userAdvice() = when (this) {
    DensityLevel.LOW -> "Good time to travel. Platforms are clear."
    DensityLevel.MEDIUM -> "Moderate crowd nearby. Expect some delays."
    DensityLevel.HIGH -> "Heavy crowding detected. Consider waiting."
    DensityLevel.DANGER -> "Dangerous density. Avoid this area now."
}

@Composable
private fun DensityLevel.primaryColor() = when (this) {
    DensityLevel.LOW -> MaterialTheme.colorScheme.primary
    DensityLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
    DensityLevel.HIGH -> MaterialTheme.colorScheme.tertiary
    DensityLevel.DANGER -> MaterialTheme.colorScheme.error
}

@Composable
private fun scoreColor(score: Float) = when {
    score < 25f -> MaterialTheme.colorScheme.primary
    score < 50f -> MaterialTheme.colorScheme.secondary
    score < 75f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
fun HomeScreen(
    crowdScore: CrowdScore?,
    devices: List<BleDevice>,
    isScanning: Boolean,
    onPermissionsGranted: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

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

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val isDark = isSystemInDarkTheme()
    val textGradientBrush = if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFF4D4D),
                Color(0xFFFF7643)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF990000),
                Color(0xFFD32F2F)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CrowdSense",
            style = TextStyle(
                brush = textGradientBrush,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.5).sp
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "CROWD INTELLIGENCE",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        val level = crowdScore?.level ?: DensityLevel.LOW
        val levelColor = level.primaryColor()

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
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
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = levelColor.copy(alpha = 0.12f),
                        spotColor = levelColor.copy(alpha = 0.18f)
                    )
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                levelColor.copy(alpha = 0.10f),
                                colors.surfaceContainerHigh,
                                colors.surfaceContainerLow
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (crowdScore == null) {
                        Text(
                            text = "...",
                            color = colors.outline,
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Default
                        )
                        Text(
                            text = "scanning",
                            color = colors.outline,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Default
                        )
                    } else {
                        Text(
                            text = level.userLabel().uppercase(),
                            color = levelColor,
                            fontSize = when (level) {
                                DensityLevel.LOW -> 22.sp
                                DensityLevel.MEDIUM -> 16.sp
                                else -> 14.sp
                            },
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${crowdScore.totalNearby} devices nearby",
                            color = colors.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (crowdScore != null) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = levelColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = if (crowdScore != null) level.userAdvice()
                    else "Starting crowd detection...",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (crowdScore != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickStatCard(
                    label = "App Users",
                    value = crowdScore.appUserCount.toString(),
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    label = "Anonymous",
                    value = crowdScore.anonymousCount.toString(),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    label = "Score",
                    value = "%.1f".format(crowdScore.score),
                    color = scoreColor(crowdScore.score),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(
            text = "NEARBY DENSITY",
            color = colors.onSurfaceVariant,
            fontSize = 10.sp,
            fontFamily = FontFamily.Default,
            letterSpacing = 2.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            MapScreen(
                deviceCount = devices.size,
                densityLevel = crowdScore?.level
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isScanning) "Live — sensing your surroundings"
                else "Go to start Dev screen to start sensing",
                color = if (isScanning) colors.primary else colors.outline,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuickStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceContainerHigh)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default
            )
            Text(
                text = label,
                color = colors.onSurfaceVariant,
                fontSize = 9.sp,
                fontFamily = FontFamily.Default,
                letterSpacing = 1.sp
            )
        }
    }
}

@Preview
@Composable
fun PreviewHomeScreenLight() {
    BLETheme(darkTheme = false) {
        HomeScreen(
            crowdScore = CrowdScore(
                score = 50f,
                appUserCount = 2,
                anonymousCount = 3,
                totalNearby = 5,
                avgRssi = 25f,
                cellularPressure = 30f,
                level = DensityLevel.LOW
            ),
            devices = listOf(),
            isScanning = true
        ) { }
    }
}

@Preview
@Composable
fun PreviewHomeScreenDark() {
    BLETheme(darkTheme = true) {
        HomeScreen(
            crowdScore = CrowdScore(
                score = 50f,
                appUserCount = 2,
                anonymousCount = 3,
                totalNearby = 5,
                avgRssi = 25f,
                cellularPressure = 30f,
                level = DensityLevel.LOW
            ),
            devices = listOf(),
            isScanning = true
        ) { }
    }
}

