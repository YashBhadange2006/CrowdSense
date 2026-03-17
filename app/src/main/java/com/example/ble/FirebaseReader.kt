package com.example.ble

import android.util.Log
import com.google.firebase.database.*
import com.example.ble.bluetooth.DensityLevel
import org.osmdroid.util.GeoPoint

data class RemoteCrowdPoint(
    val lat      : Double,
    val lng      : Double,
    val score    : Float,
    val level    : String,
    val geohash  : String,
    val timestamp: Long
)

object FirebaseReader {

    private val db = FirebaseDatabase
        .getInstance("https://crowdsense-4c6d9-default-rtdb.asia-southeast1.firebasedatabase.app")
        .reference

    private var listener: ValueEventListener? = null

    /**
     * Listens to /latest node in real time.
     * Every time any phone uploads, this fires and returns
     * all current crowd points across all locations.
     */
    fun listenToLatest(onUpdate: (List<RemoteCrowdPoint>) -> Unit) {
        // Remove any existing listener first
        stopListening()

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val points = mutableListOf<RemoteCrowdPoint>()

                for (geohashSnap in snapshot.children) {
                    try {
                        val lat       = geohashSnap.child("lat").getValue(Double::class.java) ?: continue
                        val lng       = geohashSnap.child("lng").getValue(Double::class.java) ?: continue
                        val score     = geohashSnap.child("score").getValue(Float::class.java) ?: 0f
                        val level     = geohashSnap.child("level").getValue(String::class.java) ?: "LOW"
                        val geohash   = geohashSnap.child("geohash").getValue(String::class.java) ?: ""
                        val timestamp = geohashSnap.child("timestamp").getValue(Long::class.java) ?: 0L

                        // Only include readings from last 10 minutes
                        // Stale data misleads commuters
                        val ageMinutes = (System.currentTimeMillis() - timestamp) / 60_000
                        if (ageMinutes > 10) continue

                        points.add(
                            RemoteCrowdPoint(
                                lat       = lat,
                                lng       = lng,
                                score     = score,
                                level     = level,
                                geohash   = geohash,
                                timestamp = timestamp
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("FirebaseReader", "Error parsing point: ${e.message}")
                    }
                }

                Log.d("FirebaseReader", "Got ${points.size} crowd points from Firebase")
                onUpdate(points)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseReader", "Firebase read cancelled: ${error.message}")
            }
        }

        db.child("latest").addValueEventListener(listener!!)
    }

    fun stopListening() {
        listener?.let {
            db.child("latest").removeEventListener(it)
        }
        listener = null
    }
}