package com.example.ble.userinterface.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.RemoteCrowdPoint
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.stations.Station
import com.example.ble.stations.StationCrowdMatcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    stations: List<Station>,
    remotePoints: List<RemoteCrowdPoint>,
    onStationClick: (Station) -> Unit,
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FIND A STATION",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize      = 22.sp,
                            fontWeight    = FontWeight.Bold,
                            fontFamily    = FontFamily.Default,
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            text = "check crowd before you arrive",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize      = 11.sp,
                            fontFamily    = FontFamily.Default,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                windowInsets = WindowInsets(top = 0.dp,bottom = 0.dp)
            )
        }
    ) { innerPadding ->

        var query by remember { mutableStateOf("") }
        val filtered = remember(stations, query) {
            stations.filter { st ->
                st.name.contains(query, ignoreCase = true) ||
                        st.geohash.contains(query, ignoreCase = true)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (query.isEmpty()) {
                    Text(
                        "Search station name...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Default
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Default
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (stations.isEmpty()) {
                Text(
                    text = "Loading stations…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Default
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.id }) { station ->
                        StationCard(
                            station = station,
                            remotePoints = remotePoints,
                            onClick = { onStationClick(station) }
                        )
                    }
                }
            }
        }
    }
}

private fun levelFromFirebase(level: String): DensityLevel = when (level.uppercase()) {
    "MEDIUM" -> DensityLevel.MEDIUM
    "HIGH" -> DensityLevel.HIGH
    "DANGER" -> DensityLevel.DANGER
    else -> DensityLevel.LOW
}

@Composable
private fun levelColor(level: DensityLevel?): Color {
    val colors = MaterialTheme.colorScheme
    return when (level) {
        null -> colors.onSurfaceVariant
        DensityLevel.LOW -> colors.primary
        DensityLevel.MEDIUM -> colors.secondary
        DensityLevel.HIGH -> colors.tertiary
        DensityLevel.DANGER -> colors.error
    }
}

@Composable
private fun StationCard(
    station: Station,
    remotePoints: List<RemoteCrowdPoint>,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val match = remember(remotePoints, station.id) {
        StationCrowdMatcher.match(station, remotePoints)
    }
    val point = match?.point
    val level = point?.let { levelFromFirebase(it.level) }
    val badgeColor = levelColor(level)
    val badgeLabel = when (level) {
        null -> "NO DATA"
        else -> level.name
    }

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colors.surfaceBright

        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    color = colors.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default
                )
                Text(
                    text = "Tap for crowd trend",
                    color = colors.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default
                )
                Text(
                    text = station.geohash,
                    color = colors.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default
                )
                if (match?.approximate == true && point != null) {
                    Text(
                        text = "Live level from nearby reading (~${point.geohash.take(6)}…)",
                        color = colors.outline,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.12f))
                    .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = badgeLabel,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Search Screen - Populated State")
@Composable
fun PreviewSearchScreen() {
    val mockStations = listOf(
        Station(
            id = "st-01",
            name = "Downtown Terminal",
            lat = 40.7128,
            lng = -74.0060,
            geohash = "dr5reg",
            lines = listOf("Red Line", "Blue Line"),
            type = "Metro"
        ),
        Station(
            id = "st-02",
            name = "Greenwood Station",
            lat = 40.7250,
            lng = -74.0100,
            geohash = "dr5rek",
            lines = listOf("Orange Line"),
            type = "Suburban"
        )
    )
    val mockRemotePoints = listOf(
        RemoteCrowdPoint(
            lat = 40.7130,
            lng = -74.0062,
            score = 72.5f,
            level = "HIGH",
            geohash = "dr5reg",
            timestamp = 1718875785000L
        ),
        RemoteCrowdPoint(
            lat = 40.7252,
            lng = -74.0102,
            score = 24.0f,
            level = "LOW",
            geohash = "dr5rek",
            timestamp = 1718875785000L
        )
    )
    SearchScreen(
        stations = mockStations,
        remotePoints = mockRemotePoints,
        onStationClick = { }
    )
}

@Preview
@Composable
fun PreviewStationCardHighCrowd() {
    val mockStation = Station(
        id = "st-01",
        name = "Central Terminal",
        lat = 40.7128,
        lng = -74.0060,
        geohash = "dr5reg",
        lines = listOf("Red Line", "Blue Line", "Green Line"),
        type = "Metro"
    )

    val mockRemotePoints = listOf(
        RemoteCrowdPoint(
            lat = 40.7128,
            lng = -74.0060,
            score = 88.5f,
            level = "HIGH",
            geohash = "dr5reg",
            timestamp = 1718875785000L
        )
    )

    Box(modifier = Modifier.padding(16.dp)) {
        StationCard(
            station = mockStation,
            remotePoints = mockRemotePoints,
            onClick = {}
        )
    }
}
