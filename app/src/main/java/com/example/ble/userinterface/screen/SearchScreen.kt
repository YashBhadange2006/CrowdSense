package com.example.ble.userinterface.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.RemoteCrowdPoint
import com.example.ble.bluetooth.DensityLevel
import com.example.ble.stations.Station
import com.example.ble.stations.StationCrowdMatcher

@Composable
fun SearchScreen(
    stations: List<Station>,
    remotePoints: List<RemoteCrowdPoint>,
    onStationClick: (Station) -> Unit,
) {
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
            .background(Color(0xFF0A0E14))
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "FIND A STATION",
            color = Color(0xFFE8F0FE),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 3.sp
        )
        Text(
            text = "check crowd before you arrive",
            color = Color(0xFF6B7E99),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111827))
                .border(1.dp, Color(0xFF1E2D3D), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (query.isEmpty()) {
                Text(
                    "Search station name...",
                    color = Color(0xFF3D5068),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(
                    color = Color(0xFFE8F0FE),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(Color(0xFF00E5FF)),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (stations.isEmpty()) {
            Text(
                text = "Loading stations…",
                color = Color(0xFF6B7E99),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

private fun levelFromFirebase(level: String): DensityLevel = when (level.uppercase()) {
    "MEDIUM" -> DensityLevel.MEDIUM
    "HIGH" -> DensityLevel.HIGH
    "DANGER" -> DensityLevel.DANGER
    else -> DensityLevel.LOW
}

@Composable
private fun StationCard(
    station: Station,
    remotePoints: List<RemoteCrowdPoint>,
    onClick: () -> Unit,
) {
    val match = remember(remotePoints, station.id) {
        StationCrowdMatcher.match(station, remotePoints)
    }
    val point = match?.point
    val level = point?.let { levelFromFirebase(it.level) }
    val levelColor = when (level) {
        null -> Color(0xFF3D5068)
        DensityLevel.LOW -> Color(0xFF00FF9C)
        DensityLevel.MEDIUM -> Color(0xFFFFB800)
        DensityLevel.HIGH -> Color(0xFFFF7A00)
        DensityLevel.DANGER -> Color(0xFFFF3B3B)
    }
    val badgeLabel = when (level) {
        null -> "NO DATA"
        else -> level.name
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF1E2D3D), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                color = Color(0xFFE8F0FE),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Tap for crowd trend",
                color = Color(0xFF3D5068),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = station.geohash,
                color = Color(0xFF6B7E99),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (match?.approximate == true && point != null) {
                Text(
                    text = "Live level from nearby reading (~${point.geohash.take(6)}…)",
                    color = Color(0xFF5A6B82),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(levelColor.copy(alpha = 0.12f))
                .border(1.dp, levelColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = badgeLabel,
                color = levelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}
