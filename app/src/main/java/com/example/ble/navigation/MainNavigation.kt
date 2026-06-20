package com.example.ble.navigation

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ble.RemoteCrowdPoint
import com.example.ble.bluetooth.BleDevice
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.stations.Station
import com.example.ble.userinterface.screen.DevScreen
import com.example.ble.userinterface.screen.HomeScreen
import com.example.ble.userinterface.screen.InsightsScreen
import com.example.ble.userinterface.screen.SearchScreen
import org.osmdroid.util.GeoPoint

private data class NavItem <T: Screen> (val route: T, val label : String, val icon : ImageVector)
@Composable
fun MainNavigation(
    devices: List<BleDevice>,
    crowdScore: CrowdScore?,
    isScanning: Boolean,
    stations: List<Station>,
    remotePoints: List<RemoteCrowdPoint>,
    location: GeoPoint?,
    onStopScan: () -> Unit,
    onStartScan: () -> Unit,
    onThemeToggle: () -> Unit,
    darkTheme: Boolean

){
    val navController = rememberNavController()
    val colorScheme = MaterialTheme.colorScheme

    val bottomTabs = remember {
        listOf(
            NavItem(Screen.Home, "Home", Icons.Filled.Home),
            NavItem(Screen.Search, "Search", Icons.Filled.Search),
            NavItem(Screen.InsightsDashboard, "Insights", Icons.Filled.BarChart),
            NavItem(Screen.Dev, "Dev", Icons.Filled.BugReport)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ){
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = colorScheme.surface, tonalElevation = 0.dp) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    bottomTabs.forEach { tab ->
                        val isSelected = currentDestination?.hierarchy?.any { dest ->
                            dest.hasRoute(tab.route::class) ||
                                    (tab.route is Screen.Search && currentDestination.hasRoute<Screen.StationInsights>())
                        } == true

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Screen.Home) {saveState = true}
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            ) },
                            label = { Text(text = tab.label, fontSize = 10.sp)},
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colorScheme.primary,
                                selectedTextColor = colorScheme.primary,
                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                unselectedTextColor = colorScheme.onSurfaceVariant,
                                indicatorColor = colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            },
        ){ innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable <Screen.Home> {
                    HomeScreen(
                        crowdScore = crowdScore,
                        devices = devices,
                        isScanning = isScanning,
                        onPermissionsGranted = { }
                    )
                }
                composable<Screen.Search> {
                    SearchScreen(
                        stations = stations,
                        remotePoints = remotePoints,
                        onStationClick = { station ->
                            navController.navigate(Screen.StationInsights(geohash = station.geohash))
                        }
                    )
                }
                composable<Screen.InsightsDashboard> {
                    InsightsScreen(
                        location = location,
                        stationGeohash = null,
                    )
                }
                composable<Screen.StationInsights> { entry ->
                    val args = entry.toRoute<Screen.StationInsights>()
                    val canBack = navController.previousBackStackEntry != null

                    InsightsScreen(
                        location = location,
                        stationGeohash = args.geohash,
                        canNavigateBack = canBack,
                        onBack = { navController.popBackStack()}
                    )
                }
                composable<Screen.Dev> {
                    DevScreen(
                        devices = devices,
                        crowdScore = crowdScore,
                        isScanning = isScanning,
                        remotePoints = remotePoints,
                        onStopScan = onStopScan,
                        onStartScan = onStartScan
                    )
                }
            }
        }

        IconButton(
            onClick = onThemeToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = if(darkTheme) Icons.Filled.Brightness7 else Icons.Filled.Brightness4,
                contentDescription = "Toggle Theme",
                tint = colorScheme.onSurface
            )
        }
    }
}