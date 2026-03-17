package com.example.ble.userinterface.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.CrowdDataRepository
import com.example.ble.CrowdDataRepository.TimeView
import com.example.ble.CrowdDataRepository.ChartPoint
import com.example.ble.PredictionResult
import com.example.ble.util.LocationHelper
import org.osmdroid.util.GeoPoint

// ── Colors ────────────────────────────────────────────────────────────────────
private val BgDeep     = Color(0xFF0A0E14)
private val BgCard     = Color(0xFF111827)
private val Divider    = Color(0xFF1E2D3D)
private val TextPrime  = Color(0xFFE8F0FE)
private val TextMuted  = Color(0xFF6B7E99)
private val TextDim    = Color(0xFF3D5068)
private val AccentCyan = Color(0xFF00E5FF)

private fun scoreColor(score: Float) = when {
    score == 0f  -> Color(0xFF2A3A4A)   // no data — dim blue
    score < 3f   -> Color(0xFF00FF9C)
    score < 7f   -> Color(0xFFFFB800)
    score < 12f  -> Color(0xFFFF7A00)
    else         -> Color(0xFFFF3B3B)
}

private fun levelColor(level: String) = when (level) {
    "LOW"    -> Color(0xFF00FF9C)
    "MEDIUM" -> Color(0xFFFFB800)
    "HIGH"   -> Color(0xFFFF7A00)
    "DANGER" -> Color(0xFFFF3B3B)
    else     -> Color(0xFF00FF9C)
}

@Composable
fun InsightsScreen(location: GeoPoint?) {
    val context = LocalContext.current

    var resolvedLocation  by remember { mutableStateOf(location) }
    var readings          by remember { mutableStateOf<List<com.example.ble.CrowdReading>>(emptyList()) }
    var prediction        by remember { mutableStateOf<PredictionResult?>(null) }
    var selectedView      by remember { mutableStateOf(TimeView.HOUR) }
    var isLoading         by remember { mutableStateOf(true) }
    var chartPoints       by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }

    // Resolve location if not passed in
    LaunchedEffect(Unit) {
        if (resolvedLocation == null) {
            LocationHelper.getLastLocation(context) { lat, lon ->
                resolvedLocation = GeoPoint(lat, lon)
            }
        }
    }

    // Fetch readings whenever location resolves
    LaunchedEffect(resolvedLocation) {
        val loc = resolvedLocation ?: return@LaunchedEffect
        isLoading = true

        CrowdDataRepository.fetchNearbyReadings(
            targetLat = loc.latitude,
            targetLng = loc.longitude,
            radiusM   = 100.0,
            limit     = 50
        ) { fetched ->
            readings   = fetched
            prediction = CrowdDataRepository.linearRegression(fetched)
            chartPoints = CrowdDataRepository.groupReadingsForChart(fetched, selectedView)
            isLoading  = false
        }
    }

    // Regroup chart when view changes
    LaunchedEffect(selectedView, readings) {
        chartPoints = CrowdDataRepository.groupReadingsForChart(readings, selectedView)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text          = "CROWD INSIGHTS",
            color         = TextPrime,
            fontSize      = 18.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 3.sp
        )
        Text(
            text      = "${readings.size} readings · 100m radius",
            color     = TextMuted,
            fontSize  = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            LoadingCard()
        } else if (readings.isEmpty()) {
            NoDataCard()
        } else {
            // ── Prediction card ───────────────────────────────────────────
            prediction?.let { pred ->
                PredictionCard(pred)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Live trend sparkline ──────────────────────────────────────
            SparklineCard(readings = readings)

            Spacer(modifier = Modifier.height(16.dp))

            // ── Time view dropdown ────────────────────────────────────────
            TimeViewSelector(
                selected  = selectedView,
                onChange  = { selectedView = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Bar chart ─────────────────────────────────────────────────
            BarChartCard(
                points   = chartPoints,
                view     = selectedView,
                readings = readings
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Legend ────────────────────────────────────────────────────
            Legend()
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Prediction Card ───────────────────────────────────────────────────────────

@Composable
private fun PredictionCard(pred: PredictionResult) {
    val color = levelColor(pred.predictedLevel)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text          = "AI PREDICTION · 15 MIN AHEAD",
                color         = TextMuted,
                fontSize      = 9.sp,
                fontFamily    = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = pred.predictedLevel,
                        color      = color,
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text      = "score: ${"%.1f".format(pred.predictedScore)}",
                        color     = TextMuted,
                        fontSize  = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text       = pred.trend,
                        color      = color,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text      = "confidence: ${pred.confidence}",
                        color     = TextDim,
                        fontSize  = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ── Sparkline — last 20 readings as mini line chart ───────────────────────────

@Composable
private fun SparklineCard(
    readings: List<com.example.ble.CrowdReading>
) {
    val recent = readings.takeLast(20)
    val maxScore = recent.maxOfOrNull { it.score }?.coerceAtLeast(1f) ?: 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text          = "LIVE TREND",
                    color         = TextMuted,
                    fontSize      = 9.sp,
                    fontFamily    = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text      = "last ${recent.size} readings",
                    color     = TextDim,
                    fontSize  = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sparkline bars
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment     = Alignment.Bottom
            ) {
                recent.forEach { reading ->
                    val fraction = (reading.score / maxScore).coerceIn(0.05f, 1f)
                    val color    = scoreColor(reading.score)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamps for first and last
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text      = formatTime(recent.first().timestamp),
                    color     = TextDim,
                    fontSize  = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text      = formatTime(recent.last().timestamp),
                    color     = AccentCyan,
                    fontSize  = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ── Time View Selector ────────────────────────────────────────────────────────

@Composable
private fun TimeViewSelector(
    selected: TimeView,
    onChange: (TimeView) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeView.values().forEach { view ->
            val isSelected = view == selected
            val label      = view.name

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) AccentCyan.copy(alpha = 0.15f)
                        else BgCard
                    )
                    .border(
                        1.dp,
                        if (isSelected) AccentCyan.copy(alpha = 0.6f) else Divider,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onChange(view) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = label,
                    color      = if (isSelected) AccentCyan else TextMuted,
                    fontSize   = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ── Bar Chart ─────────────────────────────────────────────────────────────────

@Composable
private fun BarChartCard(
    points  : List<ChartPoint>,
    view    : TimeView,
    readings: List<com.example.ble.CrowdReading>
) {
    if (points.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, Divider, RoundedCornerShape(14.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No data for this period",
                color     = TextDim,
                fontSize  = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    val maxScore = points.maxOfOrNull { it.avgScore }?.coerceAtLeast(1f) ?: 1f

    // For MONTH view, only show days that have data OR current month days
    // Show label only every Nth bar to avoid crowding
    val labelEvery = when {
        points.size > 20 -> 5
        points.size > 10 -> 3
        else             -> 1
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text = when (view) {
                        TimeView.HOUR  -> "BY 5-MIN BUCKET"
                        TimeView.DAY   -> "BY HOUR OF DAY"
                        TimeView.MONTH -> "BY DAY OF MONTH"
                    },
                    color         = TextMuted,
                    fontSize      = 9.sp,
                    fontFamily    = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text      = "${readings.size} readings",
                    color     = TextDim,
                    fontSize  = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment     = Alignment.Bottom
            ) {
                points.forEach { point ->
                    val fraction = if (point.isEmpty || point.avgScore == 0f) 0.03f
                    else (point.avgScore / maxScore).coerceIn(0.03f, 1f)
                    val color    = if (point.isEmpty) TextDim.copy(alpha = 0.3f)
                    else scoreColor(point.avgScore)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Labels
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                points.forEachIndexed { index, point ->
                    Text(
                        text      = if (index % labelEvery == 0) point.label else "",
                        color     = TextDim,
                        fontSize  = 6.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier  = Modifier.weight(1f),
                        maxLines  = 1
                    )
                }
            }

            // "No data available" notice for empty periods
            val emptyCount = points.count { it.isEmpty }
            if (emptyCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text      = "· $emptyCount periods with no data yet",
                    color     = TextDim,
                    fontSize  = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ── Legend ────────────────────────────────────────────────────────────────────

@Composable
private fun Legend() {
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
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
                Text(
                    label,
                    color     = TextMuted,
                    fontSize  = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ── Loading / Empty states ────────────────────────────────────────────────────

@Composable
private fun LoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Loading readings from Firebase…",
            color     = TextMuted,
            fontSize  = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun NoDataCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No readings nearby",
                color      = TextPrime,
                fontSize   = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Walk around with the app open\nto build crowd data for this area",
                color     = TextMuted,
                fontSize  = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatTime(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = timestamp
    return "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
}