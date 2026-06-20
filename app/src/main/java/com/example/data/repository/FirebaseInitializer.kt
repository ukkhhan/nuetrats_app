package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val apiKey = BuildConfig.FIREBASE_API_KEY
                val appId = BuildConfig.FIREBASE_APP_ID
                val projectId = BuildConfig.FIREBASE_PROJECT_ID
                
                if (!apiKey.isNullOrBlank() && apiKey != "MY_FIREBASE_API_KEY") {
                    val options = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId.ifBlank { "dev-m3-id-placeholder" })
                        .setProjectId(projectId.ifBlank { "nutrivision-pro" })
                        .build()
                    FirebaseApp.initializeApp(context, options)
                    Log.d("FirebaseInitializer", "Firebase initialized programmatically with provided config fields.")
                } else {
                    // Try typical default initialization if google-services.json exists
                    try {
                        FirebaseApp.initializeApp(context)
                        Log.d("FirebaseInitializer", "Firebase initialized via default JSON assets.")
                    } catch (defaultEx: Exception) {
                        Log.w("FirebaseInitializer", "Firebase credentials are empty and default assets are missing. Operating in offline-only fallback.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseInitializer", "Error during Firebase initialization", e)
        }
    }
}
