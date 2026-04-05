package com.example.ble.stations

/**
 * Minimal station record from GeoJSON (name + coordinates).
 * Geohash matches [com.example.ble.util.GeoHashUtils] precision used by uploads (6).
 */
data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val geohash: String,
)
