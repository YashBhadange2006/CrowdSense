package com.example.ble.stations

import android.content.Context
import android.util.Log
import com.example.ble.util.GeoHashUtils
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "StationCatalog"
private const val DEFAULT_STATION_ASSET_NAME = "export.geojson"
private const val GEOHASH_PRECISION = 6

/**
 * Loads station points from a bundled GeoJSON asset.
 * Thread-safe lazy cache per process; safe to call from any thread.
 */
object StationCatalog {

    @Volatile
    private var cachedAssetName: String? = null
    @Volatile
    private var cached: List<Station>? = null

    fun load(context: Context, assetName: String = DEFAULT_STATION_ASSET_NAME): List<Station> {
        if (assetName == cachedAssetName) {
            cached?.let { return it }
        }

        synchronized(this) {
            if (assetName == cachedAssetName) {
                cached?.let { return it }
            }
            val list = try {
                val text = context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseFeatures(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $assetName", e)
                emptyList()
            }
            cachedAssetName = assetName
            cached = list
            return list
        }
    }

    fun findByGeohash(context: Context, geohash: String): Station? =
        load(context).firstOrNull { it.geohash == geohash }

    internal fun parseFeatures(jsonText: String): List<Station> {
        val root = JSONObject(jsonText)
        val features: JSONArray = root.optJSONArray("features") ?: return emptyList()
        val byId = LinkedHashMap<String, Station>()

        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val props = feature.optJSONObject("properties") ?: continue
            val name = props.optString("name").trim()
            if (name.isEmpty()) continue

            val geom = feature.optJSONObject("geometry") ?: continue
            if (!"Point".equals(geom.optString("type"), ignoreCase = true)) continue
            val coords = geom.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val lng = coords.getDouble(0)
            val lat = coords.getDouble(1)
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) continue

            val railway = props.optString("railway").lowercase()
            if (railway.isNotEmpty() && railway != "station" && railway != "halt") continue

            val id = feature.optString("id").ifEmpty {
                props.optString("@id").ifEmpty { "idx_$i" }
            }
            val geohash = GeoHashUtils.encode(lat, lng, GEOHASH_PRECISION)
            byId[id] = Station(
                id = id,
                name = name,
                lat = lat,
                lng = lng,
                geohash = geohash,
            )
        }

        return byId.values.sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
    }
}
