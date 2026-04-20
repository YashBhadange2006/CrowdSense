package com.example.ble.stations

/**
 * Station record from GeoJSON (name + coordinates + metadata).
 * Geohash matches [com.example.ble.util.GeoHashUtils] precision used by uploads (6).
 * Lines: rail lines serving this station (e.g., "Western", "Central").
 * Type: station classification (e.g., "Suburban", "Interchange", "Metro", "Monorail").
 */
data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val geohash: String,
    val lines: List<String> = emptyList(),
    val type: String = "Suburban",
)
