package com.example.ble.util

object GeoHashUtils {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lng: Double, precision: Int = 6): String {
        var minLat = -90.0;  var maxLat = 90.0
        var minLng = -180.0; var maxLng = 180.0
        val hash = StringBuilder()
        var bits = 0; var bitsTotal = 0; var hashValue = 0

        while (hash.length < precision) {
            if (bitsTotal % 2 == 0) {
                val mid = (minLng + maxLng) / 2
                if (lng >= mid) { hashValue = (hashValue shl 1) or 1; minLng = mid }
                else            { hashValue = hashValue shl 1;         maxLng = mid }
            } else {
                val mid = (minLat + maxLat) / 2
                if (lat >= mid) { hashValue = (hashValue shl 1) or 1; minLat = mid }
                else            { hashValue = hashValue shl 1;         maxLat = mid }
            }
            bitsTotal++
            if (++bits == 5) {
                hash.append(BASE32[hashValue])
                bits = 0; hashValue = 0
            }
        }
        return hash.toString()
    }
}