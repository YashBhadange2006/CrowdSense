package com.example.ble

import android.app.Application
import com.google.firebase.FirebaseApp

class BleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // This is called BEFORE any Activity or Service starts
        FirebaseApp.initializeApp(this)
    }
}