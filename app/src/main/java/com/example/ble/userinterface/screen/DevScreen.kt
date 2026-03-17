package com.example.ble.userinterface.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ble.RemoteCrowdPoint
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.CrowdScore

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