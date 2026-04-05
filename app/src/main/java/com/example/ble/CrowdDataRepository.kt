package com.example.ble

import android.util.Log
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*


data class PredictionResult(
    val predictedScore : Float,
    val predictedLevel : String,
    val trend          : String,   // "Rising ↑", "Stable →", "Easing ↓"
    val confidence     : String,   // "Low", "Medium", "High"
    val minutesAhead   : Int = 15
)

object CrowdDataRepository {

    private val fmtTime = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
    private val fmtDateTime = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
    private val fmtDayMonth = SimpleDateFormat("d MMM", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }

    private val fmtDateOnly = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }

    /** First → last reading time span for chart headers. */
    fun readingsTimeWindowLabel(readings: List<CrowdReading>): String {
        if (readings.isEmpty()) return ""
        val sorted = readings.sortedBy { it.timestamp }
        val a = sorted.first().timestamp
        val b = sorted.last().timestamp
        return if (a == b) fmtDateTime.format(Date(a))
        else "${fmtDateTime.format(Date(a))} → ${fmtDateTime.format(Date(b))}"
    }

    /** Short chart mode label for UI (user-facing). */
    fun chartModeShortLabel(view: TimeView): String = when (view) {
        TimeView.HOUR  -> "5-minute slots"
        TimeView.DAY   -> "Local hour"
        TimeView.MONTH -> "Calendar days"
    }

    /** How old the latest sample is — helps users judge trust. */
    fun dataFreshnessUserLine(readings: List<CrowdReading>, nowMs: Long = System.currentTimeMillis()): String {
        if (readings.isEmpty()) return "No data yet"
        val latest = readings.maxOf { it.timestamp }
        val ageMin = ((nowMs - latest) / 60_000L).toInt().coerceAtLeast(0)
        return when {
            ageMin <= 1  -> "Live — updated moments ago"
            ageMin < 10  -> "Updated ~${ageMin} min ago"
            ageMin < 60  -> "Updated ~${ageMin} min ago"
            ageMin < 180 -> "Getting older (~${ageMin / 60} h) — use as a guide only"
            else         -> "Stale (~${ageMin / 60} h+) — check again before you travel"
        }
    }

    /** Station charts: pull readings near the stop (GPS vs OSM geohash often differ). */
    const val STATION_NEARBY_RADIUS_M = 700.0

    /** Merge Firebase pulls (e.g. exact geohash + radius) without duplicate points. */
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
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun bucketSamplesLabel(minTs: Long, maxTs: Long): String {
        if (!sameCalendarDay(minTs, maxTs)) {
            return "${fmtDateTime.format(Date(minTs))} → ${fmtDateTime.format(Date(maxTs))}"
        }
        return if (minTs == maxTs) fmtDateTime.format(Date(minTs))
        else "${fmtTime.format(Date(minTs))}–${fmtTime.format(Date(maxTs))}"
    }

    private val db = FirebaseDatabase
        .getInstance("https://crowdsense-4c6d9-default-rtdb.asia-southeast1.firebasedatabase.app")
        .reference

    // ── Haversine distance between two coords in metres ───────────────────
    fun distanceMetres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Reads last 50 readings from Firebase across ALL geohashes.
     * Merges any readings within 100m of the target location
     * into one cluster — treats them as the same physical place.
     *
     * Returns readings sorted oldest → newest (for regression).
     */
    fun fetchNearbyReadings(
        targetLat  : Double,
        targetLng  : Double,
        radiusM    : Double = 100.0,
        limit      : Int    = 50,
        onResult   : (List<CrowdReading>) -> Unit
    ) {
        // Read ALL geohashes under /readings
        db.child("readings").get().addOnSuccessListener { snapshot ->
            val allReadings = mutableListOf<CrowdReading>()

            for (geohashSnap in snapshot.children) {
                // Each geohash has push-id children
                for (pushSnap in geohashSnap.children) {
                    try {
                        val lat  = pushSnap.child("lat").getValue(Double::class.java)  ?: continue
                        val lng  = pushSnap.child("lng").getValue(Double::class.java)  ?: continue
                        val dist = distanceMetres(targetLat, targetLng, lat, lng)

                        // Only include if within radius of target location
                        if (dist > radiusM) continue

                        val score     = pushSnap.child("score").getValue(Float::class.java)   ?: 0f
                        val level     = pushSnap.child("level").getValue(String::class.java)  ?: "LOW"
                        val timestamp = pushSnap.child("timestamp").getValue(Long::class.java) ?: 0L
                        val geohash   = pushSnap.child("geohash").getValue(String::class.java) ?: ""

                        allReadings.add(
                            CrowdReading(
                                timestamp = timestamp,
                                score     = score,
                                level     = level,
                                lat       = lat,
                                lng       = lng,
                                geohash   = geohash
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("CrowdRepo", "Parse error: ${e.message}")
                    }
                }
            }

            // Sort oldest → newest, take last N
            val sorted = allReadings
                .sortedBy { it.timestamp }
                .takeLast(limit)

            Log.d("CrowdRepo", "Found ${sorted.size} readings within ${radiusM}m of target")
            onResult(sorted)

        }.addOnFailureListener { e ->
            Log.e("CrowdRepo", "Firebase read failed: ${e.message}")
            onResult(emptyList())
        }
    }

    /**
     * Reads historical readings for a single geohash bucket under /readings/{geohash}.
     * Prefer this for station-scoped charts (matches how uploads are stored).
     */
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
                            level = level,
                        )
                    )
                } catch (e: Exception) {
                    Log.e("CrowdRepo", "Parse error (geohash): ${e.message}")
                }
            }
            val sorted = list.sortedBy { it.timestamp }.takeLast(limit.coerceAtLeast(1))
            Log.d("CrowdRepo", "Geohash $geohash → ${sorted.size} readings (cap $limit)")
            onResult(sorted)
        }.addOnFailureListener { e ->
            Log.e("CrowdRepo", "Geohash read failed: ${e.message}")
            onResult(emptyList())
        }
    }

    // ── Linear Regression ─────────────────────────────────────────────────
    /**
     * On-device linear regression.
     * Input:  list of (timestamp, score) pairs sorted oldest → newest
     * Output: predicted score 15 minutes ahead
     *
     * Uses time in minutes as X axis, score as Y axis.
     * Formula: y = intercept + slope * x
     * Predicted at x = last_x + 15
     */
    fun linearRegression(readings: List<CrowdReading>): PredictionResult? {
        if (readings.size < 3) return null

        val firstTs = readings.first().timestamp

        // Convert to (minutes_since_start, score) pairs
        val points = readings.map { r ->
            val x = (r.timestamp - firstTs) / 60_000.0   // minutes
            val y = r.score.toDouble()
            Pair(x, y)
        }

        val n    = points.size.toDouble()
        val xBar = points.sumOf { it.first }  / n
        val yBar = points.sumOf { it.second } / n

        val numerator   = points.sumOf { (x, y) -> (x - xBar) * (y - yBar) }
        val denominator = points.sumOf { (x, y) -> (x - xBar).pow(2) }

        if (denominator == 0.0) return null

        val slope     = numerator / denominator
        val intercept = yBar - slope * xBar

        // Predict 15 minutes beyond the LAST reading
        val lastX          = points.last().first
        val predictX       = lastX + 15.0
        val predictedScore = (intercept + slope * predictX)
            .coerceIn(0.0, 20.0)
            .toFloat()

        // Trend based on slope
        val trend = when {
            slope >  0.05 -> "Rising ↑"
            slope < -0.05 -> "Easing ↓"
            else          -> "Stable →"
        }

        // Confidence based on how many readings we have
        val confidence = when {
            readings.size >= 30 -> "High"
            readings.size >= 10 -> "Medium"
            else                -> "Low"
        }

        val predictedLevel = when {
            predictedScore < 10f -> "LOW"
            predictedScore < 25f -> "MEDIUM"
            predictedScore < 45f -> "HIGH"
            else                 -> "DANGER"
        }

        Log.d("Regression", "slope=${"%.3f".format(slope)} " +
                "intercept=${"%.3f".format(intercept)} " +
                "predicted=${"%.2f".format(predictedScore)} " +
                "trend=$trend")

        return PredictionResult(
            predictedScore = predictedScore,
            predictedLevel = predictedLevel,
            trend          = trend,
            confidence     = confidence
        )
    }

    // ── Group readings by time bucket for chart ───────────────────────────
    enum class TimeView { HOUR, DAY, MONTH }

    data class ChartPoint(
        val label             : String,
        val avgScore          : Float,
        val count             : Int,
        val isEmpty           : Boolean = false,
        /** Tap / detail: what time window this bucket represents */
        val bucketDescription : String = "",
        /** Chronological ordering & stable list keys (start of bucket in timeline). */
        val bucketStartMillis : Long = 0L,
    )

    fun groupReadingsForChart(
        readings : List<CrowdReading>,
        view     : TimeView
    ): List<ChartPoint> {
        if (readings.isEmpty()) return emptyList()

        return when (view) {

            TimeView.HOUR -> {
                // Group by 5-minute buckets; sort by real time, not "HH:mm" string (fixes midnight / multi-day).
                val buckets = readings
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
                buckets
            }

            TimeView.DAY -> {
                // Group by hour of day (0–23); order is already chronological 00 → 23.
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
                            append(":00–")
                            append(String.format(Locale.ENGLISH, "%02d", hour))
                            append(":59 · ")
                            append(group.size)
                            append(" samples · dates ")
                            append(fmtDayMonth.format(Date(minTs)))
                            append(" → ")
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
                            bucketDescription = "Local hour ${String.format(Locale.ENGLISH, "%02d", hour)}:00–${String.format(Locale.ENGLISH, "%02d", hour)}:59 · no samples in loaded window",
                            bucketStartMillis = bucketStart
                        )
                    }
                }
            }

            TimeView.MONTH -> {
                // One bar per calendar day from first reading → last reading (not device "current month").
                val sorted = readings.sortedBy { it.timestamp }
                val minTs = sorted.first().timestamp
                val maxTs = sorted.last().timestamp
                val byDayStart = readings.groupBy { startOfDayUtcMillis(it.timestamp) }

                val labelSameYear = SimpleDateFormat("d MMM", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
                val labelWithYear = SimpleDateFormat("d MMM yy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
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
                                append(" → ")
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