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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.userinterface.screen.*
import org.osmdroid.util.GeoPoint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

private val BgDeep = Color(0xFF0A0E14)
private val NavBg  = Color(0xFF0D1520)

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",     Icons.Filled.Home)
    object Search   : Screen("search",   "Search",   Icons.Filled.Search)
    object Insights : Screen("insights", "Insights", Icons.Filled.BarChart)
    object Dev      : Screen("dev",      "Dev",      Icons.Filled.BugReport)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    1001
                )
            }
        }

        setContent {
            var devices      by remember { mutableStateOf(CrowdSenseService.latestDevices) }
            var crowdScore   by remember { mutableStateOf(CrowdSenseService.latestScore) }
            var location     by remember { mutableStateOf<GeoPoint?>(null) }
            var remotePoints by remember { mutableStateOf<List<RemoteCrowdPoint>>(emptyList()) }

            // Wire service callbacks to UI state
            DisposableEffect(Unit) {
                CrowdSenseService.onDeviceUpdate = { devices = it }
                CrowdSenseService.onScoreUpdate  = { crowdScore = it }
                onDispose {
                    CrowdSenseService.onDeviceUpdate = null
                    CrowdSenseService.onScoreUpdate  = null
                }
            }

            // Firebase listener at app level — never interrupted by navigation
            LaunchedEffect(Unit) {
                FirebaseReader.listenToLatest { points ->
                    remotePoints = points
                }
            }
            DisposableEffect(Unit) {
                onDispose { FirebaseReader.stopListening() }
            }

            val navController = rememberNavController()
            val items = listOf(Screen.Home, Screen.Search, Screen.Insights, Screen.Dev)

            // Read isRunning as state so Compose reacts to changes
            val isRunning = CrowdSenseService.isRunning

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDeep)
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(
                            containerColor = NavBg,
                            tonalElevation = 0.dp
                        ) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route

                            items.forEach { screen ->
                                val selected = currentRoute == screen.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick  = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector        = screen.icon,
                                            contentDescription = screen.label,
                                            tint = if (selected) Color(0xFF00E5FF)
                                            else Color(0xFF3D5068)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text     = screen.label,
                                            color    = if (selected) Color(0xFF00E5FF)
                                            else Color(0xFF3D5068),
                                            fontSize = androidx.compose.ui.unit.TextUnit(
                                                10f,
                                                androidx.compose.ui.unit.TextUnitType.Sp
                                            )
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController    = navController,
                        startDestination = Screen.Home.route,
                        modifier         = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                crowdScore           = crowdScore,
                                devices              = devices,
                                isScanning           = isRunning,
                                onPermissionsGranted = { }
                            )
                        }
                        composable(Screen.Search.route) {
                            SearchScreen(crowdScore = crowdScore)
                        }
                        composable(Screen.Insights.route) {
                            InsightsScreen(location = location)
                        }
                        composable(Screen.Dev.route) {
                            DevScreen(
                                devices      = devices,
                                crowdScore   = crowdScore,
                                isScanning   = isRunning,
                                remotePoints = remotePoints,
                                onStopScan   = { stopCrowdService() },
                                onStartScan  = { startCrowdService() }
                            )
                        }
                    }
                }
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

    // Nothing in onDestroy — service manages its own lifecycle
    override fun onDestroy() {
        super.onDestroy()
    }
}