package com.example.ble.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class BleAdvertiser(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val advertiser: BluetoothLeAdvertiser? =
        bluetoothManager.adapter?.bluetoothLeAdvertiser

    // Unique ID per install — so each phone has a different name
    private val uniqueId = UUID.randomUUID().toString().take(8).uppercase()
    val deviceName = "CrowdSense_$uniqueId"  // e.g. "CrowdSense_A1B2C3D4"

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BleAdvertiser", "Advertising started as $deviceName")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BleAdvertiser", "Advertising failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {

        if (advertiser == null) {
            Log.w("BleAdvertiser", "Advertiser not available — permission may not be granted yet")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER) // saves battery
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(false)  // we don't need connections, just detection
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)  // this broadcasts "CrowdSense_XXXXXXXX"
            .setIncludeTxPowerLevel(true) // helps other phones calculate distance
            .build()

        // Set the device name before advertising
        bluetoothManager.adapter?.name = deviceName

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }
}