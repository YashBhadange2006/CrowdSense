package com.example.ble.bluetooth

class DeviceCache {

    private val cache = mutableMapOf<String, BleDevice>()

    fun updateDevice(device: BleDevice): List<BleDevice> {
        cache[device.address] = device
        return cache.values.toList()
    }

    // This was the missing function
    fun getRecentDevices(withinMillis: Long): List<BleDevice> {
        val cutoff = System.currentTimeMillis() - withinMillis
        return cache.values.filter { it.lastSeen >= cutoff }
    }

    fun clear() {
        cache.clear()
    }
}