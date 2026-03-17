package com.example.ble.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.ble.FirebaseUploader
import com.example.ble.util.CellularSignalData
import com.example.ble.util.CellularSignalMonitor
import com.example.ble.util.RssiDistanceCalculator
import org.osmdroid.util.GeoPoint


data class CrowdScore(
    val score: Float,
    val appUserCount: Int,
    val anonymousCount: Int,
    val totalNearby: Int,
    val avgRssi: Float,
    val cellularPressure: Float,
    val level: DensityLevel
)

enum class DensityLevel { LOW, MEDIUM, HIGH, DANGER }


class BleScanner(
    context: Context,
    private val onDeviceFound: (List<BleDevice>) -> Unit,
    private val onCrowdScoreUpdated: (CrowdScore) -> Unit,
    private val userId: String = "anon_${System.currentTimeMillis()}"
) {

    var currentLocation: GeoPoint? = null
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager.adapter

    private val scanner: BluetoothLeScanner? =
        bluetoothAdapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val deviceCache = DeviceCache()

    private val cellularMonitor = CellularSignalMonitor(context)
    // This MUST be at class level
    private val scanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = try {
                device.name ?: result.scanRecord?.deviceName
            } catch (e: SecurityException) {
                "Unknown"
            }

            val rssi = result.rssi
            val txPower = result.scanRecord?.txPowerLevel
                ?.takeIf { it != Integer.MIN_VALUE }  // Android returns MIN_VALUE if unavailable
                ?: -59 // fallback: standard BLE calibrated power at 1 meter

            val distance = RssiDistanceCalculator.calculateDistance(rssi,txPower)
            val bleDevice = BleDevice(
                name = name ?: "Unknown",
                address = device.address,
                rssi = rssi,
                distance = distance,
                lastSeen = System.currentTimeMillis()
            )

            val updatedList = deviceCache.updateDevice(bleDevice)
            onDeviceFound(updatedList)
            computeAndEmitCrowdScore(updatedList)


        }
    }

    /**
     * Combines BLE device data + cellular signal data into one CrowdScore.
     * This is the central aggregation point for all signals.
     */


    private var lastUploadTime = 0L
    private val UPLOAD_INTERVAL = 5 * 60 * 1000L  // 5 minutes

    @RequiresApi(Build.VERSION_CODES.P)
    private fun computeAndEmitCrowdScore(devices: List<BleDevice>) {

        // Only devices seen in last 30 seconds within 15 meters
        val nearbyDevices = devices.filter {
            System.currentTimeMillis() - it.lastSeen < 30_000 && it.distance < 15.0
        }

        val appUsers = nearbyDevices.filter { it.isAppUser }
        val anonymousDevices = nearbyDevices.filter { !it.isAppUser }

        // BLE weighted score — Wirz et al. inspired weighting
        val bleScore = (appUsers.size * 3.0f) + (anonymousDevices.size * 1.0f)

        // Average RSSI penalty — lower avg RSSI = more contention
        val avgRssi = if (nearbyDevices.isNotEmpty()) {
            nearbyDevices.map { it.rssi }.average().toFloat()
        } else 0f
        val rssiPenalty = if (avgRssi < -75f) 1.5f else 1.0f

        // Cellular crowd pressure from CellularSignalMonitor
        // via RssiDistanceCalculator.calculateCellularCrowdPressure()
        val cellularData: CellularSignalData? = cellularMonitor.getCurrentSignalData()
        val cellularScore = (cellularData?.crowdPressureScore ?: 0f) * 2.0f

        // Final combined score
        val finalScore = (bleScore * rssiPenalty) + cellularScore

        val crowdScore = CrowdScore(
            score = finalScore,
            appUserCount = appUsers.size,
            anonymousCount = anonymousDevices.size,
            totalNearby = nearbyDevices.size,
            avgRssi = avgRssi,
            cellularPressure = cellularData?.crowdPressureScore ?: 0f,
            level = when {
                finalScore < 3f  -> DensityLevel.LOW
                finalScore < 7f  -> DensityLevel.MEDIUM
                finalScore < 12f -> DensityLevel.HIGH
                else             -> DensityLevel.DANGER
            }
        )

        onCrowdScoreUpdated(crowdScore)
        val now = System.currentTimeMillis()
        if (now - lastUploadTime >= UPLOAD_INTERVAL) {
            lastUploadTime = now
            currentLocation?.let { location ->
                FirebaseUploader.uploadReading(
                    crowdScore = crowdScore,
                    location = location,
                    userId = userId
                )
            }
        }

        Log.d("Firebase", "Location: $currentLocation, lastUpload: $lastUploadTime")
    }
    @SuppressLint("MissingPermission")
    fun startContinuousScan(reportInterval: Long = 30_000) {
        if (isScanning) return
        isScanning = true

        scanner?.startScan(scanCallback)

        // Periodic crowd score report even if no new devices found
        handler.post(object : Runnable {
            @RequiresApi(Build.VERSION_CODES.P)
            override fun run() {
                if (isScanning) {
                    val current = deviceCache.getRecentDevices(30_000)
                    computeAndEmitCrowdScore(current)
                    handler.postDelayed(this, reportInterval)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        scanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
        cellularMonitor.resetBaseline()
    }
}

