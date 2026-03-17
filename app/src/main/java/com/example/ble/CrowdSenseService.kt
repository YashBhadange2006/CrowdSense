package com.example.ble

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import com.example.ble.bluetooth.BleAdvertiser
import com.example.ble.bluetooth.BleScanner
import com.example.ble.bluetooth.CrowdScore
import com.example.ble.bluetooth.BleDevice
import com.example.ble.util.LocationHelper
import org.osmdroid.util.GeoPoint

class CrowdSenseService : Service() {

    private lateinit var scanner    : BleScanner
    private lateinit var advertiser : BleAdvertiser

    companion object {
        const val CHANNEL_ID = "crowdsense_channel"
        const val NOTIF_ID   = 1

        // Shared state — MainActivity reads these
        var latestScore    : CrowdScore? = null
        var latestDevices  : List<BleDevice> = emptyList()
        var isRunning by mutableStateOf(false)
            private set

        // Callbacks registered by MainActivity
        var onScoreUpdate  : ((CrowdScore) -> Unit)? = null
        var onDeviceUpdate : ((List<BleDevice>) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()

        advertiser = BleAdvertiser(this)
        scanner    = BleScanner(
            context             = this,
            onDeviceFound       = { devices ->
                latestDevices = devices
                onDeviceUpdate?.invoke(devices)
            },
            onCrowdScoreUpdated = { score ->
                latestScore = score
                onScoreUpdate?.invoke(score)
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        createNotificationChannel()

        val notifIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notifIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YATRI is active")
            .setContentText("Sensing crowd density")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIF_ID, notification)

        // Get location then start scanning
        LocationHelper.getLastLocation(this) { lat, lon ->
            scanner.currentLocation = GeoPoint(lat, lon)
            Log.d("CrowdSenseService", "Location set: $lat, $lon")
        }

        scanner.startContinuousScan()
        advertiser.startAdvertising()
        isRunning = true

        Log.d("CrowdSenseService", "Service started")
        return START_STICKY  // restart if killed by Android
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.stopScan()
        advertiser.stopAdvertising()
        isRunning = false
        Log.d("CrowdSenseService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "YATRI Background Sensing",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps crowd sensing active in background"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}