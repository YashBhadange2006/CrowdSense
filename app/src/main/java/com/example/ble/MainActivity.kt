package com.example.ble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ble.bluetooth.BleAdvertiser
import com.example.ble.userinterface.screen.ScanScreen

private val BgDeep = Color(0xFF0A0E14)

class MainActivity : ComponentActivity() {

    private lateinit var bleAdvertiser: BleAdvertiser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start advertising this phone as a CrowdSense device
        // so other phones scanning nearby give it 3x weight
        bleAdvertiser = BleAdvertiser(this)

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDeep)
                    .systemBarsPadding()
            ) {
                ScanScreen(
                    onPermissionsGranted = {
                        bleAdvertiser.startAdvertising()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleAdvertiser.stopAdvertising()
    }
}