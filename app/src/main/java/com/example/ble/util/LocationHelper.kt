package com.example.ble.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*

object LocationHelper {

    private var fusedClient: FusedLocationProviderClient? = null

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