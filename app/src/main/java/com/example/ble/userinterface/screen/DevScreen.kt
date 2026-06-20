package com.example.ble.userinterface.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.ble.RemoteCrowdPoint
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.bluetooth.DensityLevel

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