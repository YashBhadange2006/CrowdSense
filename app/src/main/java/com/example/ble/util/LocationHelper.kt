package com.example.ble.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*

// Just a Diagram for my future understanding/reference

//    [Call getLastLocation]
//    │
//    ▼
//    ┌──────────────┐
//    │ Check Cache  │───(Found Cached Location)───► [Return Lat/Lon Immediately]
//    └──────────────┘
//    │
//    (Cache Null)
//    │
//    ▼
//    ┌───────────────────────┐
//    │ Request Fresh Fix     │
//    │ High Accuracy (GPS)   │
//    └───────────────────────┘
//    │
//    ▼
//    ┌───────────────────────┐
//    │ Deliver Lat/Lon       │
//    │ Stop Hardware Updates │
//    └───────────────────────┘

object LocationHelper {

    private var fusedClient: FusedLocationProviderClient? = null

    // This function checks for Cached Location,
    // in case Google Map requested the location recently,
    // Android stores it in the cache
    @SuppressLint("MissingPermission")
    fun getLastLocation(
        context: Context,
        onLocation: (lat: Double, lon: Double) -> Unit
    ) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        fusedClient = client

        // First try to get last known location (instant, no wait)
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocation(location.latitude, location.longitude)
            } else {
                // Last location was null — request a fresh one
                requestFreshLocation(client, onLocation)
            }
        }.addOnFailureListener {
            // Last location failed — request fresh
            requestFreshLocation(client, onLocation)
        }
    }

    // If the cache is empty/null returned from .lastLocation
    // then this function forces the hardware to wake up and find
    // Here the Priority is high accuracy which forces device to use
    // GPS, Wi-Fi and cell towers to get most precise location.
    // Location taken 1 time in 5 seconds polling, if somewhere
    // else app its 1s our app will only take location every 2s to prevent app overlading main thread
    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(
        client: FusedLocationProviderClient,
        onLocation: (lat: Double, lon: Double) -> Unit
    ) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L   // request every 5 seconds
        ).apply {
            setMinUpdateIntervalMillis(2000L)
            setMaxUpdates(1)   // just get one fix then stop
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    onLocation(location.latitude, location.longitude)
                    // Stop updates after first fix
                    client.removeLocationUpdates(this)
                }
            }
        }

        client.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )
    }
}