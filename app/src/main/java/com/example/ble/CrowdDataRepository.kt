package com.example.ble

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class PredictionResult(
    val predictedScore: Float,
    val predictedLevel: String,
    val trend: String,
    val confidence: String,
    val minutesAhead: Int = 15
)

object CrowdDataRepository {

    private val indiaTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val fmtTime = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = indiaTimeZone }
    private val fmtDateTime = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).apply { timeZone = indiaTimeZone }
    private val fmtDayMonth = SimpleDateFormat("d MMM", Locale.ENGLISH).apply { timeZone = indiaTimeZone }
    private val fmtDateOnly = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).apply { timeZone = indiaTimeZone }
    private val fmtHistoryDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { timeZone = indiaTimeZone }

    private val historicalWeights = listOf(-1 to 3, -2 to 2, -3 to 1)

    private val db by lazy {
        FirebaseDatabase
            .getInstance("https://crowdsense-4c6d9-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference
    }

    fun readingsTimeWindowLabel(readings: List<CrowdReading>): String {
        if (readings.isEmpty()) return ""
        val sorted = readings.sortedBy { it.timestamp }
        val a = sorted.first().timestamp
        val b = sorted.last().timestamp
        return if (a == b) fmtDateTime.format(Date(a))
        else "${fmtDateTime.format(Date(a))} -> ${fmtDateTime.format(Date(b))}"
    }

    fun chartModeShortLabel(view: TimeView): String = when (view) {
        TimeView.HOUR -> "5-minute slots"
        TimeView.DAY -> "Local hour"
        TimeView.MONTH -> "Calendar days"
    }

    fun dataFreshnessUserLine(readings: List<CrowdReading>, nowMs: Long = System.currentTimeMillis()): String {
        if (readings.isEmpty()) return "No data yet"
        val latest = readings.maxOf { it.timestamp }
        val ageMin = ((nowMs - latest) / 60_000L).toInt().coerceAtLeast(0)
        return when {
            ageMin <= 1 -> "Live - updated moments ago"
            ageMin < 60 -> "Updated ~${ageMin} min ago"
            ageMin < 180 -> "Getting older (~${ageMin / 60} h) - use as a guide only"
            else -> "Stale (~${ageMin / 60} h+) - check again before you travel"
        }
    }

    const val STATION_NEARBY_RADIUS_M = 700.0

    fun mergeReadingsDeduped(vararg batches: List<CrowdReading>, limit: Int = 500): List<CrowdReading> {
        val cap = limit.coerceAtLeast(1)
        return batches.asSequence()
            .flatMap { it.asSequence() }
            .distinctBy { Triple(it.timestamp, it.lat, it.lng) }
            .sortedBy { it.timestamp }
            .toList()
            .takeLast(cap)
    }

    private fun startOfDayUtcMillis(ts: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun addDaysMillis(dayStart: Long, days: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = dayStart
        c.add(Calendar.DAY_OF_MONTH, days)
        return c.timeInMillis
    }

    private fun sameCalendarDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun bucketSamplesLabel(minTs: Long, maxTs: Long): String {
        if (!sameCalendarDay(minTs, maxTs)) {
            return "${fmtDateTime.format(Date(minTs))} -> ${fmtDateTime.format(Date(maxTs))}"
        }
        return if (minTs == maxTs) fmtDateTime.format(Date(minTs))
        else "${fmtTime.format(Date(minTs))}-${fmtTime.format(Date(maxTs))}"
    }

    // Lat-Long to Metres conversion function
    fun distanceMetres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // Fetches Crowd data in the radius of 100M
    fun fetchNearbyReadings(
        targetLat: Double,
        targetLng: Double,
        radiusM: Double = 100.0,
        limit: Int = 50,
        onResult: (List<CrowdReading>) -> Unit
    ) {
        db.child("readings").get().addOnSuccessListener { snapshot ->
            val allReadings = mutableListOf<CrowdReading>()

            for (geohashSnap in snapshot.children) {
                for (pushSnap in geohashSnap.children) {
                    try {
                        val lat = pushSnap.child("lat").getValue(Double::class.java) ?: continue
                        val lng = pushSnap.child("lng").getValue(Double::class.java) ?: continue
                        val dist = distanceMetres(targetLat, targetLng, lat, lng)
                        if (dist > radiusM) continue

                        val score = pushSnap.child("score").getValue(Float::class.java) ?: 0f
                        val level = pushSnap.child("level").getValue(String::class.java) ?: "LOW"
                        val timestamp = pushSnap.child("timestamp").getValue(Long::class.java) ?: 0L
                        val geohash = pushSnap.child("geohash").getValue(String::class.java) ?: ""

                        allReadings.add(
                            CrowdReading(
                                timestamp = timestamp,
                                score = score,
                                level = level,
                                lat = lat,
                                lng = lng,
                                geohash = geohash
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("CrowdRepo", "Parse error: ${e.message}")
                    }
                }
            }

            val sorted = allReadings.sortedBy { it.timestamp }.takeLast(limit)
            Log.d("CrowdRepo", "Found ${sorted.size} readings within ${radiusM}m of target")
            onResult(sorted)
        }.addOnFailureListener { e ->
            Log.e("CrowdRepo", "Firebase read failed: ${e.message}")
            onResult(emptyList())
        }
    }

    // This function is used to fetch data for that specific location
    // which is then used to display Crowd Predictions and Crowd density charts
    fun fetchReadingsForGeohash(
        geohash: String,
        limit: Int = 500,
        onResult: (List<CrowdReading>) -> Unit
    ) {
        if (geohash.isBlank()) {
            onResult(emptyList())
            return
        }
        db.child("readings").child(geohash).get().addOnSuccessListener { snapshot ->
            val list = mutableListOf<CrowdReading>()
            for (pushSnap in snapshot.children) {
                try {
                    val lat = pushSnap.child("lat").getValue(Double::class.java) ?: continue
                    val lng = pushSnap.child("lng").getValue(Double::class.java) ?: continue
                    val score = pushSnap.child("score").getValue(Float::class.java) ?: 0f
                    val level = pushSnap.child("level").getValue(String::class.java) ?: "LOW"
                    val timestamp = pushSnap.child("timestamp").getValue(Long::class.java) ?: 0L
                    val gh = pushSnap.child("geohash").getValue(String::class.java) ?: geohash
                    list.add(
                        CrowdReading(
                            timestamp = timestamp,
                            lat = lat,
                            lng = lng,
                            geohash = gh,
                            score = score,
                            level = level
                        )
                    )
                } catch (e: Exception) {
                    Log.e("CrowdRepo", "Parse error (geohash): ${e.message}")
                }
            }
            val sorted = list.sortedBy { it.timestamp }.takeLast(limit.coerceAtLeast(1))
            Log.d("CrowdRepo", "Geohash $geohash -> ${sorted.size} readings (cap $limit)")
            onResult(sorted)
        }.addOnFailureListener { e ->
            Log.e("CrowdRepo", "Geohash read failed: ${e.message}")
            onResult(emptyList())
        }
    }

    fun fetchPrediction(
        readings: List<CrowdReading>,
        preferredGeohash: String? = null,
        onResult: (PredictionResult?) -> Unit
    ) {
        val geohash = preferredGeohash?.takeIf { it.isNotBlank() } ?: inferPredictionGeohash(readings)
        if (geohash == null) {
            onResult(fallbackPredictionFromReadings(readings))
            return
        }

        val anchorTs = readings.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        val hourKey = hourKeyFor(anchorTs)

        db.child("history").child(geohash).get()
            .addOnSuccessListener { snapshot ->
                var weightedSum = 0f
                var totalWeight = 0
                var matchedDays = 0
                var supportingSamples = 0

                for ((dayOffset, weight) in historicalWeights) {
                    val dateKey = historyDateKeyFor(anchorTs, dayOffset)
                    val hourSnapshot = snapshot.child(dateKey).child(hourKey)
                    val avgScore = hourSnapshot.child("avgScore").getValue(Float::class.java) ?: continue
                    val readingCount = hourSnapshot.child("readingCount").getValue(Int::class.java) ?: 0

                    weightedSum += avgScore * weight
                    totalWeight += weight
                    matchedDays += 1
                    supportingSamples += readingCount
                }

                if (totalWeight > 0) {
                    val predictedScore = (weightedSum / totalWeight).coerceAtLeast(0f)
                    val latestScore = readings.maxByOrNull { it.timestamp }?.score
                    Log.d(
                        "Prediction",
                        "Weighted historical prediction geohash=$geohash hour=$hourKey matchedDays=$matchedDays predicted=${"%.2f".format(predictedScore)}"
                    )
                    onResult(
                        buildPredictionResult(
                            predictedScore = predictedScore,
                            latestScore = latestScore,
                            matchedDays = matchedDays,
                            supportingSamples = supportingSamples
                        )
                    )
                } else {
                    Log.d("Prediction", "No /history samples for geohash=$geohash hour=$hourKey, using fallback")
                    onResult(fallbackPredictionFromReadings(readings))
                }
            }
            .addOnFailureListener { e ->
                Log.e("Prediction", "History read failed for geohash=$geohash: ${e.message}")
                onResult(fallbackPredictionFromReadings(readings))
            }
    }

    private fun inferPredictionGeohash(readings: List<CrowdReading>): String? =
        readings
            .filter { it.geohash.isNotBlank() }
            .groupBy { it.geohash }
            .maxWithOrNull(
                compareBy<Map.Entry<String, List<CrowdReading>>> { it.value.size }
                    .thenBy { entry -> entry.value.maxOfOrNull { it.timestamp } ?: 0L }
            )
            ?.key

    private fun fallbackPredictionFromReadings(readings: List<CrowdReading>): PredictionResult? {
        if (readings.isEmpty()) return null

        val latest = readings.maxByOrNull { it.timestamp } ?: return null
        val anchorHour = hourKeyFor(latest.timestamp)
        val anchorDay = historyDateKeyFor(latest.timestamp, 0)

        val priorDayAverages = readings
            .filter { it.timestamp > 0L && hourKeyFor(it.timestamp) == anchorHour }
            .groupBy { historyDateKeyFor(it.timestamp, 0) }
            .filterKeys { it != anchorDay }
            .toList()
            .sortedByDescending { (dateKey, _) -> dateKey }
            .take(historicalWeights.size)

        if (priorDayAverages.isNotEmpty()) {
            var weightedSum = 0f
            var totalWeight = 0
            var supportingSamples = 0

            priorDayAverages.forEachIndexed { index, (_, group) ->
                val weight = historicalWeights.getOrNull(index)?.second ?: return@forEachIndexed
                val avgScore = group.map { it.score }.average().toFloat()
                weightedSum += avgScore * weight
                totalWeight += weight
                supportingSamples += group.size
            }

            if (totalWeight > 0) {
                return buildPredictionResult(
                    predictedScore = (weightedSum / totalWeight).coerceAtLeast(0f),
                    latestScore = latest.score,
                    matchedDays = priorDayAverages.size,
                    supportingSamples = supportingSamples
                )
            }
        }

        return buildPredictionResult(
            predictedScore = latest.score.coerceAtLeast(0f),
            latestScore = latest.score,
            matchedDays = 0,
            supportingSamples = 1
        )
    }

    private fun buildPredictionResult(
        predictedScore: Float,
        latestScore: Float?,
        matchedDays: Int,
        supportingSamples: Int
    ): PredictionResult {
        val delta = latestScore?.let { predictedScore - it } ?: 0f
        val trend = when {
            delta > 3f -> "Rising ↑"
            delta < -3f -> "Easing ↓"
            else -> "Stable →"
        }

        val confidence = when {
            matchedDays >= 3 && supportingSamples >= 6 -> "High"
            matchedDays >= 2 -> "Medium"
            else -> "Low"
        }

        val predictedLevel = when {
            predictedScore < 10f -> "LOW"
            predictedScore < 25f -> "MEDIUM"
            predictedScore < 45f -> "HIGH"
            else -> "DANGER"
        }

        return PredictionResult(
            predictedScore = predictedScore,
            predictedLevel = predictedLevel,
            trend = trend,
            confidence = confidence
        )
    }

    private fun historyDateKeyFor(anchorTs: Long, dayOffset: Int): String {
        val cal = Calendar.getInstance(indiaTimeZone).apply { timeInMillis = anchorTs }
        cal.add(Calendar.DAY_OF_MONTH, dayOffset)
        return fmtHistoryDateKey.format(cal.time)
    }

    private fun hourKeyFor(ts: Long): String =
        Calendar.getInstance(indiaTimeZone).apply { timeInMillis = ts }
            .get(Calendar.HOUR_OF_DAY)
            .toString()

    enum class TimeView { HOUR, DAY, MONTH }

    data class ChartPoint(
        val label: String,
        val avgScore: Float,
        val count: Int,
        val isEmpty: Boolean = false,
        val bucketDescription: String = "",
        val bucketStartMillis: Long = 0L
    )

    fun groupReadingsForChart(
        readings: List<CrowdReading>,
        view: TimeView
    ): List<ChartPoint> {
        if (readings.isEmpty()) return emptyList()

        return when (view) {
            TimeView.HOUR -> {
                readings
                    .groupBy { r ->
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = r.timestamp
                        val h = cal.get(Calendar.HOUR_OF_DAY)
                        val m = (cal.get(Calendar.MINUTE) / 5) * 5
                        "%02d:%02d".format(h, m)
                    }
                    .entries
                    .sortedBy { (_, group) -> group.minOf { it.timestamp } }
                    .map { (label, group) ->
                        val minTs = group.minOf { it.timestamp }
                        val maxTs = group.maxOf { it.timestamp }
                        val desc = buildString {
                            append(bucketSamplesLabel(minTs, maxTs))
                            append(" · ")
                            append(group.size)
                            append(if (group.size == 1) " reading" else " readings")
                            append(" · avg ")
                            append("%.1f".format(group.map { it.score }.average()))
                        }
                        ChartPoint(
                            label = label,
                            avgScore = group.map { it.score }.average().toFloat(),
                            count = group.size,
                            bucketDescription = desc,
                            bucketStartMillis = minTs
                        )
                    }
            }

            TimeView.DAY -> {
                val anchorDay = startOfDayUtcMillis(readings.minOf { it.timestamp })
                val byHour = readings.groupBy { r ->
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = r.timestamp
                    cal.get(Calendar.HOUR_OF_DAY)
                }

                (0..23).map { hour ->
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = anchorDay
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val bucketStart = cal.timeInMillis

                    val group = byHour[hour]
                    if (group != null) {
                        val minTs = group.minOf { it.timestamp }
                        val maxTs = group.maxOf { it.timestamp }
                        val desc = buildString {
                            append("Local hour ")
                            append(String.format(Locale.ENGLISH, "%02d", hour))
                            append(":00-")
                            append(String.format(Locale.ENGLISH, "%02d", hour))
                            append(":59 · ")
                            append(group.size)
                            append(" samples · dates ")
                            append(fmtDayMonth.format(Date(minTs)))
                            append(" -> ")
                            append(fmtDayMonth.format(Date(maxTs)))
                        }
                        ChartPoint(
                            label = "${String.format(Locale.ENGLISH, "%02d", hour)}h",
                            avgScore = group.map { it.score }.average().toFloat(),
                            count = group.size,
                            bucketDescription = desc,
                            bucketStartMillis = bucketStart
                        )
                    } else {
                        ChartPoint(
                            label = "${String.format(Locale.ENGLISH, "%02d", hour)}h",
                            avgScore = 0f,
                            count = 0,
                            isEmpty = true,
                            bucketDescription = "Local hour ${String.format(Locale.ENGLISH, "%02d", hour)}:00-${String.format(Locale.ENGLISH, "%02d", hour)}:59 · no samples in loaded window",
                            bucketStartMillis = bucketStart
                        )
                    }
                }
            }

            TimeView.MONTH -> {
                val sorted = readings.sortedBy { it.timestamp }
                val minTs = sorted.first().timestamp
                val maxTs = sorted.last().timestamp
                val byDayStart = readings.groupBy { startOfDayUtcMillis(it.timestamp) }

                val labelSameYear = SimpleDateFormat("d MMM", Locale.ENGLISH).apply { timeZone = indiaTimeZone }
                val labelWithYear = SimpleDateFormat("d MMM yy", Locale.ENGLISH).apply { timeZone = indiaTimeZone }
                val calMin = Calendar.getInstance().apply { timeInMillis = minTs }
                val calMax = Calendar.getInstance().apply { timeInMillis = maxTs }
                val sameYear = calMin.get(Calendar.YEAR) == calMax.get(Calendar.YEAR)
                val fmtLabel = if (sameYear) labelSameYear else labelWithYear

                val result = mutableListOf<ChartPoint>()
                var dayStart = startOfDayUtcMillis(minTs)
                val endDay = startOfDayUtcMillis(maxTs)
                while (dayStart <= endDay) {
                    val group = byDayStart[dayStart].orEmpty()
                    val label = fmtLabel.format(Date(dayStart))
                    if (group.isNotEmpty()) {
                        val gMin = group.minOf { it.timestamp }
                        val gMax = group.maxOf { it.timestamp }
                        val desc = buildString {
                            append(fmtDateTime.format(Date(gMin)))
                            if (gMin != gMax) {
                                append(" -> ")
                                append(fmtDateTime.format(Date(gMax)))
                            }
                            append(" · ")
                            append(group.size)
                            append(if (group.size == 1) " reading" else " readings")
                            append(" · avg ")
                            append("%.1f".format(group.map { it.score }.average()))
                        }
                        result.add(
                            ChartPoint(
                                label = label,
                                avgScore = group.map { it.score }.average().toFloat(),
                                count = group.size,
                                bucketDescription = desc,
                                bucketStartMillis = dayStart
                            )
                        )
                    } else {
                        result.add(
                            ChartPoint(
                                label = label,
                                avgScore = 0f,
                                count = 0,
                                isEmpty = true,
                                bucketDescription = "${fmtDateOnly.format(Date(dayStart))} · no readings",
                                bucketStartMillis = dayStart
                            )
                        )
                    }
                    dayStart = addDaysMillis(dayStart, 1)
                }
                result
            }
        }
    }
}
