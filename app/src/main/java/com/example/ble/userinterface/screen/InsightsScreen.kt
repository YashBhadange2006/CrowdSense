package com.example.ble.userinterface.screen

import android.graphics.drawable.Icon
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.CrowdDataRepository
import com.example.ble.CrowdDataRepository.TimeView
import com.example.ble.CrowdDataRepository.ChartPoint
import com.example.ble.CrowdReading
import com.example.ble.PredictionResult
import com.example.ble.stations.Station
import com.example.ble.stations.StationCatalog
import com.example.ble.util.LocationHelper
import org.osmdroid.util.GeoPoint
import java.util.Locale

// Colors
@Composable
private fun BgDeep() = MaterialTheme.colorScheme.background
@Composable
private fun BgCard() = MaterialTheme.colorScheme.surface
@Composable
private fun Divider() = MaterialTheme.colorScheme.outlineVariant
@Composable
private fun TextPrime() = MaterialTheme.colorScheme.onSurface
@Composable
private fun TextMuted() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable
private fun TextDim() = MaterialTheme.colorScheme.outline
@Composable
private fun AccentCyan() = MaterialTheme.colorScheme.primary

@Composable
private fun scoreColor(score: Float) = when {
        score == 0f  -> MaterialTheme.colorScheme.surfaceVariant   // no data — dim blue
    score < 10f  -> MaterialTheme.colorScheme.primary
    score < 25f  -> MaterialTheme.colorScheme.secondary
    score < 45f  -> MaterialTheme.colorScheme.tertiary
    else         -> MaterialTheme.colorScheme.error
}

@Composable
private fun levelColor(level: String) = when (level) {
    "LOW"    -> MaterialTheme.colorScheme.primary
    "MEDIUM" -> MaterialTheme.colorScheme.secondary
    "HIGH"   -> MaterialTheme.colorScheme.tertiary
    "DANGER" -> MaterialTheme.colorScheme.error
    else     -> MaterialTheme.colorScheme.primary
}

@Composable
fun InsightsScreen(
    location: GeoPoint?,
    stationGeohash: String? = null,
    canNavigateBack: Boolean = false,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    var resolvedLocation by remember { mutableStateOf(location) }
    var readings         by remember { mutableStateOf<List<CrowdReading>>(emptyList()) }
    var prediction       by remember { mutableStateOf<PredictionResult?>(null) }
    var selectedView     by remember { mutableStateOf(TimeView.HOUR) }
    var isLoading        by remember { mutableStateOf(true) }
    var chartPoints      by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }

    // Station: use catalog coords for context. GPS tab: last known location when needed.
    LaunchedEffect(stationGeohash) {
        when {
            stationGeohash != null -> {
                val s = StationCatalog.findByGeohash(context, stationGeohash)
                resolvedLocation = s?.let { GeoPoint(it.lat, it.lng) } ?: resolvedLocation
            }
            resolvedLocation == null -> {
                LocationHelper.getLastLocation(context) { lat, lon ->
                    resolvedLocation = GeoPoint(lat, lon)
                }
            }
        }
    }

    // Station: merge exact geohash bucket + radius around stop (upload cell often ≠ catalog geohash).
    LaunchedEffect(stationGeohash) {
        if (stationGeohash == null) return@LaunchedEffect
        isLoading = true
        val station: Station? = StationCatalog.findByGeohash(context, stationGeohash)
        var fromGh: List<CrowdReading>? = null
        var fromNearby: List<CrowdReading>? = null
        fun applyMerge() {
            val g = fromGh ?: return
            if (station != null && fromNearby == null) return
            val merged = CrowdDataRepository.mergeReadingsDeduped(
                g,
                fromNearby ?: emptyList(),
                limit = 500
            )
            readings = merged
            chartPoints = CrowdDataRepository.groupReadingsForChart(merged, selectedView)
            isLoading = false
            CrowdDataRepository.fetchPrediction(
                readings = merged,
                preferredGeohash = stationGeohash
            ) { prediction = it }
        }
        CrowdDataRepository.fetchReadingsForGeohash(stationGeohash, limit = 500) {
            fromGh = it
            applyMerge()
        }
        if (station != null) {
            CrowdDataRepository.fetchNearbyReadings(
                targetLat = station.lat,
                targetLng = station.lng,
                radiusM = CrowdDataRepository.STATION_NEARBY_RADIUS_M,
                limit = 500
            ) {
                fromNearby = it
                applyMerge()
            }
        } else {
            fromNearby = emptyList()
            applyMerge()
        }
    }

    // GPS tab: nearby cluster when not viewing a fixed station.
    LaunchedEffect(resolvedLocation, stationGeohash) {
        if (stationGeohash != null) return@LaunchedEffect
        val loc = resolvedLocation ?: return@LaunchedEffect
        isLoading = true
        CrowdDataRepository.fetchNearbyReadings(
            targetLat = loc.latitude,
            targetLng = loc.longitude,
            radiusM = 100.0,
            limit = 50
        ) { fetched ->
            readings = fetched
            chartPoints = CrowdDataRepository.groupReadingsForChart(fetched, selectedView)
            isLoading = false
            CrowdDataRepository.fetchPrediction(readings = fetched) { prediction = it }
        }
    }

    // Regroup chart when view changes
    LaunchedEffect(selectedView, readings) {
        chartPoints = CrowdDataRepository.groupReadingsForChart(readings, selectedView)
    }

    val stationLabel = stationGeohash?.let { gh ->
        StationCatalog.findByGeohash(context, gh)?.name ?: gh
    }
    val nearestStopLine = remember(resolvedLocation, stationGeohash) {
        if (stationGeohash != null || resolvedLocation == null) null
        else {
            val loc = resolvedLocation!!
            StationCatalog.load(context).minByOrNull { st ->
                CrowdDataRepository.distanceMetres(loc.latitude, loc.longitude, st.lat, st.lng)
            }?.let { st ->
                val m = CrowdDataRepository.distanceMetres(loc.latitude, loc.longitude, st.lat, st.lng).toInt()
                "Closest stop: ${st.name} (~$m m away)"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep())
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canNavigateBack) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back",
                        tint = TextPrime()
                    )
                }
            }
            Text(
                text          = "CROWD INSIGHTS",
                color         = TextPrime(),
                fontSize      = 18.sp,
                fontWeight    = FontWeight.Bold,
                fontFamily    = FontFamily.Monospace,
                letterSpacing = 3.sp
            )
        }
        if (stationGeohash != null) {
            Text(
                text       = stationLabel ?: stationGeohash,
                color      = TextPrime(),
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 8.dp)
            )
            Text(
                text       = "Charts combine this stop's area with nearby readings when map cells differ.",
                color      = TextMuted(),
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp
            )
        } else {
            Text(
                text       = "Within ~100 m of your GPS location",
                color      = AccentCyan(),
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 8.dp)
            )
            nearestStopLine?.let { line ->
                Text(
                    text       = line,
                    color      = TextMuted(),
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            LoadingCard()
        } else if (readings.isEmpty()) {
            NoDataCard()
        } else {
            prediction?.let { pred ->
                PredictionCard(pred)
                Spacer(modifier = Modifier.height(16.dp))
            }

            SparklineCard(readings = readings, timeView = selectedView)

            Spacer(modifier = Modifier.height(16.dp))

            // // Time view dropdown
            TimeViewSelector(
                selected  = selectedView,
                onChange  = { selectedView = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bar chart
            BarChartCard(
                points   = chartPoints,
                view     = selectedView,
                readings = readings
            )

            Spacer(modifier = Modifier.height(16.dp))

            Legend()
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Prediction Card

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
                text          = "LIKELY CROWD IN ~15 MIN",
                color         = TextMuted(),
                fontSize      = 10.sp,
                fontWeight    = FontWeight.Bold,
                fontFamily    = FontFamily.Monospace,
                letterSpacing = 1.sp
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
                        text      = "Strength ${"%.0f".format(pred.predictedScore)}",
                        color     = TextMuted(),
                        fontSize  = 12.sp,
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
                        text      = "Trust: ${pred.confidence}",
                        color     = TextDim(),
                        fontSize  = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}


@Composable
private fun SparklineCard(
    readings: List<CrowdReading>,
    timeView: TimeView,
) {
    val ordered = remember(readings) { readings.sortedBy { it.timestamp } }
    val recent = remember(ordered) { ordered.takeLast(40) }
    val maxScore = recent.maxOfOrNull { it.score }?.coerceAtLeast(1f) ?: 1f
    val modeLine = remember(timeView) { CrowdDataRepository.chartModeShortLabel(timeView) }
    val freshness = remember(readings) { CrowdDataRepository.dataFreshnessUserLine(readings) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard())
            .border(1.dp, Divider(), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column {
            Text(
                text = "RECENT UPLOADS",
                color = TextMuted(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = modeLine,
                color = AccentCyan(),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = freshness,
                color = TextMuted(),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Older ←  ·  → Newer",
                color = TextDim(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                itemsIndexed(recent, key = { _, r -> r.timestamp }) { _, reading ->
                    val fraction = (reading.score / maxScore).coerceIn(0.08f, 1f)
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(96.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(scoreColor(reading.score))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (recent.isNotEmpty()) {
                Text(
                    text = CrowdDataRepository.readingsTimeWindowLabel(recent),
                    color = TextDim(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// Time View Selector

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
            val label      = when (view) {
                TimeView.HOUR  -> "5 min"
                TimeView.DAY   -> "Hour"
                TimeView.MONTH -> "Day"
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) AccentCyan().copy(alpha = 0.15f)
                        else BgCard()
                    )
                    .border(
                        1.dp,
                        if (isSelected) AccentCyan().copy(alpha = 0.6f) else Divider(),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onChange(view) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = label,
                    color      = if (isSelected) AccentCyan() else TextMuted(),
                    fontSize   = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}


@Composable
private fun BarChartCard(
    points  : List<ChartPoint>,
    view    : TimeView,
    readings: List<CrowdReading>
) {
    if (points.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard())
                .border(1.dp, Divider(), RoundedCornerShape(14.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No data for this period",
                color = TextDim(),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    var selectedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(points) { selectedIndex = -1 }

    val maxScore = points.maxOfOrNull { it.avgScore }?.coerceAtLeast(1f) ?: 1f
    val midScore = maxScore / 2f
    val modeLabel = remember(view) { CrowdDataRepository.chartModeShortLabel(view) }
    val freshness = remember(readings) { CrowdDataRepository.dataFreshnessUserLine(readings) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard())
            .border(1.dp, Divider(), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column {
            Text(
                text = "CROWD BY TIME",
                color = TextMuted(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = modeLabel,
                color = AccentCyan(),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = freshness,
                color = TextMuted(),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Tap a bar for details",
                color = TextDim(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight()
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "%.0f".format(maxScore),
                        color = TextDim(),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%.0f".format(midScore),
                        color = TextDim(),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "0",
                        color = TextDim(),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 12.dp, start = 4.dp)
                ) {
                    itemsIndexed(
                        points,
                        key = { _, p -> p.bucketStartMillis }
                    ) { index, point ->
                        val selected = index == selectedIndex
                        val fraction = if (point.isEmpty || point.avgScore == 0f) 0.06f
                        else (point.avgScore / maxScore).coerceIn(0.06f, 1f)
                        val color = if (point.isEmpty) TextDim().copy(alpha = 0.35f)
                        else scoreColor(point.avgScore)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(
                                    when (view) {
                                        TimeView.MONTH -> 68.dp
                                        TimeView.HOUR, TimeView.DAY -> 62.dp
                                    }
                                )
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedIndex = if (selectedIndex == index) -1 else index
                                }
                                .then(
                                    if (selected) Modifier.border(1.dp, AccentCyan(), RoundedCornerShape(10.dp))
                                    else Modifier
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.58f)
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(color)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = point.label,
                                color = if (selected) AccentCyan() else TextMuted(),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                lineHeight = 11.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = if (point.count > 0) "${point.count}×" else "—",
                                color = TextDim(),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            if (selectedIndex in points.indices) {
                val p = points[selectedIndex]
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentCyan().copy(alpha = 0.08f))
                        .border(1.dp, AccentCyan().copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = p.label,
                            color = AccentCyan(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = p.bucketDescription.ifEmpty {
                                if (p.isEmpty) "No readings here."
                                else "Typical score about ${String.format(Locale.ENGLISH, "%.1f", p.avgScore)} (${
                                    p.count
                                } uploads)."
                            },
                            color = TextPrime(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

// Legend

@Composable
private fun Legend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        listOf(
            "Low"    to MaterialTheme.colorScheme.primary,
            "Medium" to MaterialTheme.colorScheme.secondary,
            "High"   to MaterialTheme.colorScheme.tertiary,
            "Danger" to MaterialTheme.colorScheme.error
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
                    color     = TextMuted(),
                    fontSize  = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// // Loading / Empty states

@Composable
private fun LoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard())
            .border(1.dp, Divider(), RoundedCornerShape(14.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
            Text(
                "Loading readings from Firebase…",
            color     = TextMuted(),
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
            .background(BgCard())
            .border(1.dp, Divider(), RoundedCornerShape(14.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No readings nearby",
                color      = TextPrime(),
                fontSize   = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Walk around with the app open\nto build crowd data for this area",
                color     = TextMuted(),
                fontSize  = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Preview
@Composable
fun PreviewInsightScreen(){
    val mockLocation = GeoPoint(37.7749, -122.4194)

    InsightsScreen(
        location = mockLocation ,
        stationGeohash = "2fv9d",
        canNavigateBack = true,
        onBack = {}
    )
}

@Preview
@Composable
fun PreviewBarChartCardPopulated() {
    val mockPoints = listOf(
        ChartPoint(label = "08:00", avgScore = 20.5f, count = 3, bucketDescription = "Morning Low"),
        ChartPoint(label = "12:00", avgScore = 85.0f, count = 12, bucketDescription = "Lunch Rush Peak"),
        ChartPoint(label = "16:00", avgScore = 55.2f, count = 7, bucketDescription = "Mid Afternoon"),
        ChartPoint(label = "20:00", avgScore = 10.0f, count = 1, bucketDescription = "Night Decline")
    )
    val mockReadings = listOf(
        CrowdReading(timestamp = 1718875785000L, score = 85.0f, level = "HIGH", appUsers = 4, anonymous = 8),
        CrowdReading(timestamp = 1718875795000L, score = 20.5f, level = "LOW", appUsers = 1, anonymous = 2)
    )

    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        BarChartCard(
            points = mockPoints,
            view = TimeView.DAY,
            readings = mockReadings
        )
    }
}




