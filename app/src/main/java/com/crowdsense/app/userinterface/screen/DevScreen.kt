package com.crowdsense.app.userinterface.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.crowdsense.app.RemoteCrowdPoint
import com.crowdsense.app.bluetooth.BleDevice
import com.crowdsense.app.bluetooth.CrowdScore
import com.crowdsense.app.bluetooth.DensityLevel

@Composable
fun DevScreen(
    devices      : List<BleDevice>,
    crowdScore   : CrowdScore?,
    isScanning   : Boolean,
    remotePoints : List<RemoteCrowdPoint>,
    onStopScan   : () -> Unit,
    onStartScan  : () -> Unit
) {
    // Pass service state into ScanScreen so it doesn't manage its own scanner
    ScanScreen(
        onPermissionsGranted = { },
        externalIsScanning   = isScanning,
        onStartScan          = onStartScan,
        onStopScan           = onStopScan,
        externalDevices      = devices,
        externalCrowdScore   = crowdScore
    )
}

@Preview
@Composable
fun PreviewDevScreen() {
    DevScreen(
        devices = listOf(),
        crowdScore = CrowdScore(
            score = 50f,
            appUserCount = 2,
            anonymousCount = 3,
            totalNearby = 5,
            avgRssi = 25f,
            cellularPressure = 30f,
            level = DensityLevel.DANGER
        ),
        onStopScan = {},
        isScanning = true,
        remotePoints = listOf()
    ) {
    }
}