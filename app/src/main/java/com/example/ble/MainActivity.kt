package com.example.ble

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ble.RemoteCrowdPoint
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.stations.Station
import com.example.ble.stations.StationCatalog
import com.example.ble.ui.theme.BLETheme
import com.example.ble.userinterface.screen.DevScreen
import com.example.ble.userinterface.screen.HomeScreen
import com.example.ble.userinterface.screen.InsightsScreen
import com.example.ble.userinterface.screen.SearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ble.navigation.MainNavigation

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkBackgroundLocationPermissions()

        setContent {
            val context = LocalContext.current
            val systemInDarkTheme = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemInDarkTheme) }

            var devices by remember { mutableStateOf(CrowdSenseService.latestDevices) }
            var crowdScore by remember { mutableStateOf(CrowdSenseService.latestScore) }
            var location by remember { mutableStateOf<GeoPoint?>(null) }
            var remotePoints by remember { mutableStateOf<List<RemoteCrowdPoint>>(emptyList()) }
            var stations by remember { mutableStateOf<List<Station>>(emptyList()) }

            LaunchedEffect(Unit) {
                stations = withContext(Dispatchers.IO) {
                    StationCatalog.load(context.applicationContext)
                }
            }

            DisposableEffect(Unit) {
                CrowdSenseService.onDeviceUpdate = { devices = it }
                CrowdSenseService.onScoreUpdate = { crowdScore = it }
                onDispose {
                    CrowdSenseService.onDeviceUpdate = null
                    CrowdSenseService.onScoreUpdate = null
                }
            }

            LaunchedEffect(Unit) {
                FirebaseReader.listenToLatest { points ->
                    remotePoints = points
                }
            }
            DisposableEffect(Unit) {
                onDispose { FirebaseReader.stopListening() }
            }

            BLETheme(darkTheme = darkTheme, dynamicColor = false) {
                MainNavigation(
                    devices = devices,
                    crowdScore = crowdScore,
                    isScanning = CrowdSenseService.isRunning,
                    stations = stations,
                    remotePoints = remotePoints,
                    location = location,
                    onStopScan = { stopCrowdService() },
                    onStartScan = { startCrowdService() },
                    onThemeToggle = {darkTheme = !darkTheme},
                    darkTheme = darkTheme
                )
            }
        }
    }

    private fun checkBackgroundLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 1001)
            }
        }
    }
    private fun startCrowdService() {
        if (CrowdSenseService.isRunning) return
        val intent = Intent(this, CrowdSenseService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopCrowdService() {
        stopService(Intent(this, CrowdSenseService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
