package com.example.ble.localdb

import android.content.Context
import com.google.firebase.FirebaseApp

object CrowdDataProvider {
    private lateinit var appContext: Context
    fun initialize(context: Context) { appContext = context.applicationContext }
    fun context(): Context = appContext
    fun databaseUrl(): String = appContext.getString(com.example.ble.R.string.firebase_db_url)
        .takeIf { it.isNotBlank() }
        ?: runCatching { FirebaseApp.getInstance().options.databaseUrl }.getOrNull().orEmpty()

    // A configured Firebase project wins; local mode is automatic when the APK has no Firebase app.
    val usesFirebase: Boolean

        get() = runCatching {
            FirebaseApp.getApps(appContext).isNotEmpty() && databaseUrl().isNotBlank()
        }.getOrDefault(false)
}
