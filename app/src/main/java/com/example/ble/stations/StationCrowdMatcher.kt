package com.example.ble.stations

import com.example.ble.CrowdDataRepository
import com.example.ble.RemoteCrowdPoint

private const val MIN_PREFIX_LEN = 5
private const val MAX_DISTANCE_M = 1_200.0

/**
 * Picks the best [RemoteCrowdPoint] for a station when exact geohash does not match uploads.
 * Order: exact geohash → longest shared prefix (≥5) with tie-break by distance → nearest point within [MAX_DISTANCE_M].
 */
data class CrowdBadgeMatch(
    val point: RemoteCrowdPoint,
    /** True if not an exact geohash match (nearby cell / distance). */
    val approximate: Boolean,
)

object StationCrowdMatcher {

    fun match(station: Station, points: List<RemoteCrowdPoint>): CrowdBadgeMatch? {
        if (points.isEmpty()) return null

        points.firstOrNull { it.geohash == station.geohash }?.let {
            return CrowdBadgeMatch(it, approximate = false)
        }

        var bestPrefixPoint: RemoteCrowdPoint? = null
        var bestPrefixLen = 0
        var bestPrefixDist = Double.MAX_VALUE
        for (p in points) {
            val len = commonPrefixLength(station.geohash, p.geohash)
            if (len < MIN_PREFIX_LEN) continue
            val d = CrowdDataRepository.distanceMetres(station.lat, station.lng, p.lat, p.lng)
            if (len > bestPrefixLen || (len == bestPrefixLen && d < bestPrefixDist)) {
                bestPrefixLen = len
                bestPrefixDist = d
                bestPrefixPoint = p
            }
        }
        bestPrefixPoint?.let { return CrowdBadgeMatch(it, approximate = true) }

        val nearest = points
            .map { p ->
                p to CrowdDataRepository.distanceMetres(station.lat, station.lng, p.lat, p.lng)
            }
            .filter { it.second <= MAX_DISTANCE_M }
            .minByOrNull { it.second }
            ?: return null

        return CrowdBadgeMatch(nearest.first, approximate = true)
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }
}
