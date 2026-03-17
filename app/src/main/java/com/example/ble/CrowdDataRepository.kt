package com.example.ble

import android.util.Log
import com.google.firebase.database.*
import kotlin.math.*


data class PredictionResult(
    val predictedScore : Float,
    val predictedLevel : String,
    val trend          : String,   // "Rising ↑", "Stable →", "Easing ↓"
    val confidence     : String,   // "Low", "Medium", "High"
    val minutesAhead   : Int = 15
)

object CrowdDataRepository {

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
            predictedScore < 3f  -> "LOW"
            predictedScore < 7f  -> "MEDIUM"
            predictedScore < 12f -> "HIGH"
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
        val label    : String,
        val avgScore : Float,
        val count    : Int,
        val isEmpty  : Boolean = false
    )

    fun groupReadingsForChart(
        readings : List<CrowdReading>,
        view     : TimeView
    ): List<ChartPoint> {
        if (readings.isEmpty()) return emptyList()

        return when (view) {

            TimeView.HOUR -> {
                // Group by 5-minute buckets within the data's time range
                val buckets = readings
                    .groupBy { r ->
                        val cal = java.util.Calendar.getInstance()
                        cal.timeInMillis = r.timestamp
                        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val m = (cal.get(java.util.Calendar.MINUTE) / 5) * 5
                        "%02d:%02d".format(h, m)
                    }
                    .map { (label, group) ->
                        ChartPoint(
                            label    = label,
                            avgScore = group.map { it.score }.average().toFloat(),
                            count    = group.size
                        )
                    }
                    .sortedBy { it.label }
                buckets
            }

            TimeView.DAY -> {
                // Group by hour of day (0-23)
                val allHours = (0..23).map { it }
                val byHour   = readings.groupBy { r ->
                    val cal = java.util.Calendar.getInstance()
                    cal.timeInMillis = r.timestamp
                    cal.get(java.util.Calendar.HOUR_OF_DAY)
                }

                allHours.map { hour ->
                    val group = byHour[hour]
                    if (group != null) {
                        ChartPoint(
                            label    = "${hour}h",
                            avgScore = group.map { it.score }.average().toFloat(),
                            count    = group.size
                        )
                    } else {
                        ChartPoint(
                            label    = "${hour}h",
                            avgScore = 0f,
                            count    = 0,
                            isEmpty  = true
                        )
                    }
                }
            }

            TimeView.MONTH -> {
                // Group by day of month
                val byDay = readings.groupBy { r ->
                    val cal = java.util.Calendar.getInstance()
                    cal.timeInMillis = r.timestamp
                    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
                    val m = cal.get(java.util.Calendar.MONTH) + 1
                    "%02d/%02d".format(d, m)
                }

                // Get full month range
                val cal = java.util.Calendar.getInstance()
                val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val currentMonth = cal.get(java.util.Calendar.MONTH) + 1

                (1..daysInMonth).map { day ->
                    val key   = "%02d/%02d".format(day, currentMonth)
                    val group = byDay[key]
                    if (group != null) {
                        ChartPoint(
                            label    = key,
                            avgScore = group.map { it.score }.average().toFloat(),
                            count    = group.size
                        )
                    } else {
                        ChartPoint(
                            label    = key,
                            avgScore = 0f,
                            count    = 0,
                            isEmpty  = true   // shown as "no data" bar
                        )
                    }
                }
            }
        }
    }
}