package com.example.ble.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

class BleAdvertiser(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val advertiser: BluetoothLeAdvertiser? =
        bluetoothManager.adapter?.bluetoothLeAdvertiser

    private val handler = Handler(Looper.getMainLooper())
    private var isAdvertising = false
    private var advertiseAttempts = 0

    // Unique ID per install — shown in scan response when space allows
    private val uniqueId = UUID.randomUUID().toString().take(8).uppercase()
    val deviceName = "CrowdSense_$uniqueId"

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            advertiseAttempts = 0
            Log.d(TAG, "Advertising started — UUID + mfg tag as $deviceName")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.e(TAG, "Advertising failed: ${failureLabel(errorCode)} ($errorCode)")
            if (advertiseAttempts < 3 &&
                errorCode != AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED &&
                errorCode != AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS
            ) {
                advertiseAttempts++
                handler.postDelayed({ startAdvertisingInternal() }, 2_000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        handler.removeCallbacksAndMessages(null)
        advertiseAttempts = 0
        startAdvertisingInternal()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertisingInternal() {
        if (advertiser == null) {
            Log.w(TAG, "Advertiser not available — grant BLUETOOTH_ADVERTISE and restart scan")
            return
        }
        if (isAdvertising) {
            advertiser.stopAdvertising(advertiseCallback)
            isAdvertising = false
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            // Connectable adverts are discovered more reliably on Samsung / Xiaomi
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // Primary packet: service UUID + manufacturer tag (fits in 31-byte limit)
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(CrowdSenseBle.SERVICE_PARCEL_UUID)
            .addManufacturerData(
                CrowdSenseBle.MANUFACTURER_ID,
                CrowdSenseBle.MANUFACTURER_PAYLOAD
            )
            .setIncludeTxPowerLevel(true)
            .build()

        // Scan response: device name (optional fallback for older parsers)
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        bluetoothManager.adapter?.name = deviceName

        advertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        handler.removeCallbacksAndMessages(null)
        if (!isAdvertising) return
        advertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
    }

    private fun failureLabel(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "UNKNOWN"
    }

    companion object {
        private const val TAG = "BleAdvertiser"
    }
}
