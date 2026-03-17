package com.example.ble.userinterface.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.bluetooth.DensityLevel

data class Station(
    val name    : String,
    val platform: String,
    val line    : String
)

val STATIONS = listOf(
    Station("Thane",           "Platform 1", "Central Line"),
    Station("Thane",           "Platform 2", "Central Line"),
    Station("Mulund",          "Platform 1", "Central Line"),
    Station("Ghatkopar",       "Platform 1", "Central Line"),
    Station("Kurla",           "Platform 1", "Central Line"),
    Station("Dadar",           "Platform 1", "Central Line"),
    Station("CST",             "Platform 1", "Central Line"),
    Station("Andheri",         "Platform 1", "Western Line"),
    Station("Borivali",        "Platform 1", "Western Line"),
    Station("Churchgate",      "Platform 1", "Western Line"),
)

@Composable
fun SearchScreen(crowdScore: CrowdScore?) {
    var query by remember { mutableStateOf("") }

    val filtered = STATIONS.filter {
        it.name.contains(query, ignoreCase = true) ||
                it.line.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14))
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text          = "FIND A STATION",
            color         = Color(0xFFE8F0FE),
            fontSize      = 18.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 3.sp
        )
        Text(
            text      = "check crowd before you arrive",
            color     = Color(0xFF6B7E99),
            fontSize  = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Search bar
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
                    color     = Color(0xFF3D5068),
                    fontSize  = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value        = query,
                onValueChange = { query = it },
                textStyle    = TextStyle(
                    color      = Color(0xFFE8F0FE),
                    fontSize   = 14.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush  = SolidColor(Color(0xFF00E5FF)),
                modifier     = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered) { station ->
                StationCard(station = station, crowdScore = crowdScore)
            }
        }
    }
}

@Composable
private fun StationCard(station: Station, crowdScore: CrowdScore?) {
    // For demo — use current crowd score for all stations
    // Post-hackathon: read per-station geohash from Firebase
    val level = crowdScore?.level ?: DensityLevel.LOW
    val levelColor = when (level) {
        DensityLevel.LOW    -> Color(0xFF00FF9C)
        DensityLevel.MEDIUM -> Color(0xFFFFB800)
        DensityLevel.HIGH   -> Color(0xFFFF7A00)
        DensityLevel.DANGER -> Color(0xFFFF3B3B)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF1E2D3D), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text       = station.name,
                color      = Color(0xFFE8F0FE),
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text      = "${station.platform} · ${station.line}",
                color     = Color(0xFF6B7E99),
                fontSize  = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(levelColor.copy(alpha = 0.12f))
                .border(1.dp, levelColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text      = level.name,
                color     = levelColor,
                fontSize  = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}