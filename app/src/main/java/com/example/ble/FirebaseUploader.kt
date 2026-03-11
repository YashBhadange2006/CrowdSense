package com.example.ble

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.util.GeoHashUtils
import org.osmdroid.util.GeoPoint

data class CrowdReading(
    val timestamp: Long = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val geohash: String = "",
    val score: Float = 0f,
    val level: String = "",
    val appUsers: Int = 0,
    val anonymous: Int = 0,
    val avgRssi: Float = 0f,
    val cellPressure: Float = 0f,
    val userId: String = ""
)

object FirebaseUploader {

    private val db by lazy {
        FirebaseDatabase.getInstance("https://crowdsense-4c6d9-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference
    }

    fun uploadReading(
        crowdScore: CrowdScore,
        location: GeoPoint,
        userId: String
    ) {
        val geohash = GeoHashUtils.encode(location.latitude, location.longitude, 6)

        val reading = CrowdReading(
            timestamp    = System.currentTimeMillis(),
            lat          = location.latitude,
            lng          = location.longitude,
            geohash      = geohash,
            score        = crowdScore.score,
            level        = crowdScore.level.name,
            appUsers     = crowdScore.appUserCount,
            anonymous    = crowdScore.anonymousCount,
            avgRssi      = crowdScore.avgRssi,
            cellPressure = crowdScore.cellularPressure,
            userId       = userId
        )

        // Store under geohash for spatial queries
        db.child("readings")
            .child(geohash)
            .push()
            .setValue(reading)
            .addOnSuccessListener {
                Log.d("Firebase", "✅ Upload success — score: ${reading.score} geohash: $geohash")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "❌ Upload failed: ${e.message}")
            }

        // Also update the latest reading for quick access
        db.child("latest")
            .child(geohash)
            .setValue(reading)

        // Update hourly history
        updateHourlyHistory(geohash, crowdScore.score)
    }

    private fun updateHourlyHistory(geohash: String, score: Float) {
        val now = java.util.Calendar.getInstance()
        val date = "%04d-%02d-%02d".format(
            now.get(java.util.Calendar.YEAR),
            now.get(java.util.Calendar.MONTH) + 1,
            now.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY).toString()

        val hourRef = db
            .child("history")
            .child(geohash)
            .child(date)
            .child(hour)

        // Read current average, update it
        hourRef.get().addOnSuccessListener { snapshot ->
            val currentAvg   = snapshot.child("avgScore").getValue(Float::class.java) ?: score
            val currentCount = snapshot.child("readingCount").getValue(Int::class.java) ?: 0
            val newCount     = currentCount + 1
            val newAvg       = ((currentAvg * currentCount) + score) / newCount

            hourRef.child("avgScore").setValue(newAvg)
            hourRef.child("readingCount").setValue(newCount)
        }
    }
}