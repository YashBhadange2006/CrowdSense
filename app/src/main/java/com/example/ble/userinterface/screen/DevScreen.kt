package com.example.ble.userinterface.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.BleScanner
import com.example.ble.bluetooth.CrowdScore
import org.osmdroid.util.GeoPoint

@Composable
fun DevScreen(
    devices             : List<BleDevice>,
    crowdScore          : CrowdScore?,
    isScanning          : Boolean,
    scanner             : BleScanner,
    location            : GeoPoint?,
    onPermissionsGranted: () -> Unit,
    onScanningChanged   : (Boolean) -> Unit
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

    // Pass everything into your existing ScanScreen composable
    // Just rename your old ScanScreen to DevScanContent
    // and call it here
    ScanScreen(
        onPermissionsGranted = onPermissionsGranted
    )
}