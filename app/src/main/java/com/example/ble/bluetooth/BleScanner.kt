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
import com.google.firebase.auth.FirebaseAuth
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
) {

    private val userId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
                ?: "anon_${System.currentTimeMillis()}"

    var currentLocation: GeoPoint? = null
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager.adapter

    private val scanner: BluetoothLeScanner? =
        bluetoothAdapter?.bluetoothLeScanner

    private val ownAddress: String? = try {
        bluetoothAdapter?.address?.takeUnless { it == "02:00:00:00:00:00" }
    } catch (_: SecurityException) {
        null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val deviceCache = DeviceCache()

    private val cellularMonitor = CellularSignalMonitor(context)

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //Change its Value, rn consumes a lot of power not for continuous background use
        .setReportDelay(0L)
        .build()

    private val scanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScanner", "BLE scan failed: $errorCode")
        }
    }

    //This functions takes in all the values from scanned BLE
    // and one by one feeds it into functions to calculate Crowd Density, Update Cards, Calculate distance based on RSSI
    @RequiresApi(Build.VERSION_CODES.P)
    private fun processScanResult(result: ScanResult) {
        val device = result.device
        if (ownAddress != null && device.address.equals(ownAddress, ignoreCase = true)) return

        val record = result.scanRecord
        val fallbackName = try {
            device.name
        } catch (_: SecurityException) {
            null
        }
        val displayName = CrowdSenseBle.resolveDisplayName(record, fallbackName)
        val isAppUser = CrowdSenseBle.isAppUser(record, displayName)

        val rssi = result.rssi
        val txPower = record?.txPowerLevel
            ?.takeIf { it != Integer.MIN_VALUE }
            ?: -59

        val distance = RssiDistanceCalculator.calculateDistance(rssi, txPower)
        val bleDevice = BleDevice(
            name = displayName,
            address = device.address,
            rssi = rssi,
            distance = distance,
            lastSeen = System.currentTimeMillis(),
            isAppUser = isAppUser,
        )

        val updatedList = deviceCache.updateDevice(bleDevice)
        onDeviceFound(updatedList)
        computeAndEmitCrowdScore(updatedList)
    }

    private var lastUploadTime = 0L
    private val UPLOAD_INTERVAL = 1 * 60 * 1000L

    // This function Filters nearbyDevices as per time, distance and rssi
    // Then separates app/anon users
    // calculates BLEScore, averages out RssiValue of nearbyDevices eg. (RSSI_Device1 + RSSI_Device2)/2
    // gets cellular signal, crowd pressure score
    // then finalScore is calculated
    // crowd level range is set by Beta Testing
    @RequiresApi(Build.VERSION_CODES.P)
    private fun computeAndEmitCrowdScore(devices: List<BleDevice>) {
        val nearbyDevices = devices.filter {
            System.currentTimeMillis() - it.lastSeen < 30_000 &&
                (it.distance < 25.0 || it.rssi > -75)
        }

        val appUsers = nearbyDevices.filter { it.isAppUser }
        val anonymousDevices = nearbyDevices.filter { !it.isAppUser }

        val bleScore = (appUsers.size * 3.0f) + (anonymousDevices.size * 1.0f)

        val avgRssi = if (nearbyDevices.isNotEmpty()) {
            nearbyDevices.map { it.rssi }.average().toFloat()
        } else 0f
        val rssiPenalty = if (avgRssi < -75f) 1.5f else 1.0f

        val cellularData: CellularSignalData? = cellularMonitor.getCurrentSignalData()
        val cellularScore = (cellularData?.crowdPressureScore ?: 0f) * 2.0f

        val finalScore = (bleScore * rssiPenalty) + cellularScore

        val crowdScore = CrowdScore(
            score = finalScore,
            appUserCount = appUsers.size,
            anonymousCount = anonymousDevices.size,
            totalNearby = nearbyDevices.size,
            avgRssi = avgRssi,
            cellularPressure = cellularData?.crowdPressureScore ?: 0f,
            level = when {
                finalScore < 10f -> DensityLevel.LOW
                finalScore < 25f -> DensityLevel.MEDIUM
                finalScore < 45f -> DensityLevel.HIGH
                else             -> DensityLevel.DANGER
            }
        )

        onCrowdScoreUpdated(crowdScore)
        val now = System.currentTimeMillis()
        if (now - lastUploadTime >= UPLOAD_INTERVAL) {
            lastUploadTime = now
            if (currentLocation != null) {
                FirebaseUploader.uploadReading(
                    crowdScore = crowdScore,
                    location = currentLocation!!,
                    userId = userId
                )
                Log.d("Firebase", "Upload triggered — score: ${crowdScore.score}")
            } else {
                Log.w("Firebase", "Upload skipped — location is NULL")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startContinuousScan(reportInterval: Long = 30_000) {
        if (isScanning) return
        isScanning = true

        scanner?.startScan(null, scanSettings, scanCallback)

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
