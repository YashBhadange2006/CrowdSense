package com.example.ble.bluetooth

class DeviceCache {

    private val cache = mutableMapOf<String, BleDevice>()
    private val DEFAULT_DEVICE_EXPIRY_MS = 30_000L

    fun updateDevice(device: BleDevice): List<BleDevice> {
        pruneExpiredDevices(DEFAULT_DEVICE_EXPIRY_MS)
        cache[device.address] = device
        return cache.values.toList()
    }

    fun getRecentDevices(withinMillis: Long): List<BleDevice> {
        pruneExpiredDevices(withinMillis)
        val cutoff = System.currentTimeMillis() - withinMillis
        return cache.values.filter { it.lastSeen >= cutoff }
    }

    private fun pruneExpiredDevices(withinMillis: Long) {
        val cutoff = System.currentTimeMillis() - withinMillis
        val staleKeys = cache.filterValues { it.lastSeen < cutoff }.keys
        staleKeys.forEach { cache.remove(it) }
    }

    fun clear() {
        cache.clear()
    }
}