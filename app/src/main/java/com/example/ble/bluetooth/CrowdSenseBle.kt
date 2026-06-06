package com.example.ble.bluetooth

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import java.util.UUID

/**
 * Shared BLE identity for CrowdSense app users.
 * Detection uses service UUID + manufacturer data — not only the device name,
 * because many phones (Xiaomi, Samsung) report the phone model name instead.
 */
object CrowdSenseBle {

    val SERVICE_UUID: UUID =
        UUID.fromString("6f9a2c4e-8b1d-4e3a-9c7f-2a8e5d1b0c3f")

    val SERVICE_PARCEL_UUID = ParcelUuid(SERVICE_UUID)

    /** Internal-use manufacturer ID (0xFFFF per Bluetooth SIG). */
    const val MANUFACTURER_ID = 0xFFFF

    private val MAGIC = byteArrayOf(0x43, 0x53, 0x01) // "CS" + version

    val MANUFACTURER_PAYLOAD: ByteArray = MAGIC.copyOf()

    fun isAppUser(scanRecord: ScanRecord?, displayName: String): Boolean {
        if (scanRecord != null) {
            if (scanRecord.serviceUuids?.any { it.uuid == SERVICE_UUID } == true) return true
            val mfg = scanRecord.getManufacturerSpecificData(MANUFACTURER_ID)
            if (mfg != null && mfg.size >= MAGIC.size &&
                mfg.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
            ) return true
        }
        return displayName.startsWith("CrowdSense_")
    }

    fun resolveDisplayName(scanRecord: ScanRecord?, fallbackName: String?): String {
        val advertised = scanRecord?.deviceName?.takeIf { it.isNotBlank() }
        if (advertised != null) return advertised
        val fallback = fallbackName?.takeIf { it.isNotBlank() }
        return fallback ?: "Unknown"
    }
}
