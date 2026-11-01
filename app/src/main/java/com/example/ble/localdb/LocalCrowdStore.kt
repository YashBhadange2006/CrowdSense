package com.example.ble.localdb

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.ble.CrowdReading
import com.example.ble.RemoteCrowdPoint
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.util.GeoHashUtils
import org.osmdroid.util.GeoPoint
import java.util.concurrent.ConcurrentHashMap

object LocalCrowdStore {
    private const val MAX_READINGS = 5_000
    private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ConcurrentHashMap<Int, (List<RemoteCrowdPoint>) -> Unit>()
    private var nextListenerId = 0

    fun save(context: Context, score: CrowdScore, location: GeoPoint) {
        val now = System.currentTimeMillis()
        val hash = GeoHashUtils.encode(location.latitude, location.longitude, 6)
        val dao = LocalCrowdDatabase.get(context).readings()
        Thread {
            dao.insert(ReadingEntity(timestamp = now, lat = location.latitude, lng = location.longitude,
                geohash = hash, score = score.score, level = score.level.name))
            dao.deleteOlderThan(now - RETENTION_MS)
            dao.keepNewest(MAX_READINGS)
            publish(context)
        }.start()
    }

    fun nearby(context: Context, lat: Double, lng: Double, radius: Double, limit: Int, callback: (List<CrowdReading>) -> Unit) {
        Thread {
            val results = LocalCrowdDatabase.get(context).readings().recent(MAX_READINGS)
                .map { it.toReading() }
                .filter { com.example.ble.CrowdDataRepository.distanceMetres(lat, lng, it.lat, it.lng) <= radius }
                .take(limit.coerceAtLeast(1))
                .asReversed()
            main.post { callback(results) }
        }.start()
    }

    fun forGeohash(context: Context, hash: String, limit: Int, callback: (List<CrowdReading>) -> Unit) {
        Thread {
            val results = LocalCrowdDatabase.get(context).readings().forGeohash(hash, limit.coerceAtLeast(1))
                .map { it.toReading() }.asReversed()
            main.post { callback(results) }
        }.start()
    }

    fun listen(context: Context, callback: (List<RemoteCrowdPoint>) -> Unit): Int {
        val id = synchronized(this) { ++nextListenerId }
        listeners[id] = callback
        Thread { publish(context) }.start()
        return id
    }

    fun stop(id: Int) { listeners.remove(id) }

    private fun publish(context: Context) {
        val points = LocalCrowdDatabase.get(context).readings().recent(MAX_READINGS)
            .map { RemoteCrowdPoint(it.lat, it.lng, it.score, it.level, it.geohash, it.timestamp) }
            .distinctBy { it.geohash }
            .filter { System.currentTimeMillis() - it.timestamp <= 10 * 60_000L }
        main.post { listeners.values.forEach { it(points) } }
    }
}
