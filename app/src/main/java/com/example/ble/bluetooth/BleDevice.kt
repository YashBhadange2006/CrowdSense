package com.example.ble.bluetooth

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val distance: Double,
    val lastSeen: Long
) {
    val isAppUser: Boolean get() = name.startsWith("CrowdSense_")
}



