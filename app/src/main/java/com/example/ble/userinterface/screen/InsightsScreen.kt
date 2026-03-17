package com.example.ble.userinterface.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint

// Hardcoded demo data — replace with Firebase history reads post-hackathon
private val DEMO_HOURLY = mapOf(
    6  to 1.2f,
    7  to 3.4f,
    8  to 8.9f,
    9  to 11.2f,
    10 to 7.6f,
    11 to 4.2f,
    12 to 3.8f,
    13 to 4.1f,
    14 to 3.2f,
    15 to 4.8f,
    16 to 6.2f,
    17 to 9.8f,
    18 to 12.4f,
    19 to 10.1f,
    20 to 6.3f,
    21 to 3.1f,
    22 to 1.8f,
    23 to 0.9f
)

private fun scoreToColor(score: Float) = when {
    score < 3f  -> Color(0xFF00FF9C)
    score < 7f  -> Color(0xFFFFB800)
    score < 12f -> Color(0xFFFF7A00)
    else        -> Color(0xFFFF3B3B)
}

@Composable
fun InsightsScreen(location: GeoPoint?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14))
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text          = "CROWD INSIGHTS",
            color         = Color(0xFFE8F0FE),
            fontSize      = 18.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 3.sp
        )
        Text(
            text      = "best times to travel",
            color     = Color(0xFF6B7E99),
            fontSize  = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Best time callout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF00FF9C).copy(alpha = 0.08f))
                .border(1.dp, Color(0xFF00FF9C).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text      = "BEST TIME TO TRAVEL TODAY",
                    color     = Color(0xFF6B7E99),
                    fontSize  = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text       = "10:00 AM — 4:00 PM",
                    color      = Color(0xFF00FF9C),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text      = "Avoid 8–9 AM and 6–7 PM peak hours",
                    color     = Color(0xFF6B7E99),
                    fontSize  = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text          = "HOURLY CROWD PATTERN",
            color         = Color(0xFF6B7E99),
            fontSize      = 10.sp,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Bar chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111827))
                .border(1.dp, Color(0xFF1E2D3D), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.Bottom
                ) {
                    DEMO_HOURLY.forEach { (hour, score) ->
                        val fraction  = (score / 14f).coerceIn(0f, 1f)
                        val barColor  = scoreToColor(score)
                        val isCurrentHour = hour == java.util.Calendar.getInstance()
                            .get(java.util.Calendar.HOUR_OF_DAY)

                        Column(
                            modifier              = Modifier.weight(1f),
                            horizontalAlignment   = Alignment.CenterHorizontally,
                            verticalArrangement   = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        if (isCurrentHour) barColor
                                        else barColor.copy(alpha = 0.6f)
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Hour labels
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DEMO_HOURLY.forEach { (hour, _) ->
                        Text(
                            text      = if (hour % 3 == 0) "${hour}h" else "",
                            color     = Color(0xFF3D5068),
                            fontSize  = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier  = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                "Low"    to Color(0xFF00FF9C),
                "Medium" to Color(0xFFFFB800),
                "High"   to Color(0xFFFF7A00),
                "Danger" to Color(0xFFFF3B3B)
            ).forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                    Text(
                        label,
                        color     = Color(0xFF6B7E99),
                        fontSize  = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}