package com.example.ble


import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.osmdroid.config.Configuration
import com.example.ble.localdb.CrowdDataProvider

// Just for Anon Auth Firebase
class BleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osm", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = "CrowdSense-Android/1.0"

        FirebaseApp.initializeApp(this)
        CrowdDataProvider.initialize(this)

        // Sign in anonymously before any Firebase read/write
        if (CrowdDataProvider.usesFirebase) {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) auth.signInAnonymously()
                .addOnSuccessListener {
                    android.util.Log.d("Auth", "Signed in anonymously: ${it.user?.uid}")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("Auth", "Auth failed: ${e.message}")
                }
        }
    }
}
