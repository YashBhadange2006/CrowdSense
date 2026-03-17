package com.example.ble

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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ble.bluetooth.BleAdvertiser
import com.example.ble.bluetooth.BleScanner
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.userinterface.screen.*
import org.osmdroid.util.GeoPoint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.ui.unit.dp

private val BgDeep = Color(0xFF0A0E14)
private val NavBg  = Color(0xFF0D1520)
private val NavBorder = Color(0xFF1E2D3D)

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",     Icons.Filled.Home)
    object Search   : Screen("search",   "Search",   Icons.Filled.Search)
    object Insights : Screen("insights", "Insights", Icons.Filled.BarChart)
    object Dev      : Screen("dev",      "Dev",      Icons.Filled.BugReport)
}

class MainActivity : ComponentActivity() {

    private lateinit var bleAdvertiser: BleAdvertiser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bleAdvertiser = BleAdvertiser(this)

        setContent {
            // Shared state across all screens
            var devices    by remember { mutableStateOf(listOf<com.example.ble.bluetooth.BleDevice>()) }
            var crowdScore by remember { mutableStateOf<CrowdScore?>(null) }
            var location   by remember { mutableStateOf<GeoPoint?>(null) }
            var isScanning by remember { mutableStateOf(false) }

            val scanner = remember {
                BleScanner(
                    context             = this,
                    onDeviceFound       = { devices = it },
                    onCrowdScoreUpdated = { crowdScore = it }
                )
            }

            val navController = rememberNavController()
            val items = listOf(
                Screen.Home,
                Screen.Search,
                Screen.Insights,
                Screen.Dev
            )

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
                            tonalElevation = 0.dp,
                            modifier = Modifier.background(NavBg)
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
                                crowdScore   = crowdScore,
                                devices      = devices,
                                isScanning   = isScanning,
                                onPermissionsGranted = {
                                    bleAdvertiser.startAdvertising()
                                },
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
                                isScanning   = isScanning,
                                scanner      = scanner,
                                location     = location,
                                onPermissionsGranted = {
                                    bleAdvertiser.startAdvertising()
                                },
                                onScanningChanged = { isScanning = it }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleAdvertiser.stopAdvertising()
    }
}