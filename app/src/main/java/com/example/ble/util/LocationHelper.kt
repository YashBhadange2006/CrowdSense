package com.example.ble.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices

object LocationHelper {

    @SuppressLint("MissingPermission")
    fun getLastLocation(
        context: Context,
        onLocation: (Double, Double) -> Unit
    ) {
        val fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(context)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    onLocation(it.latitude, it.longitude)
                }
            }
    }
}
