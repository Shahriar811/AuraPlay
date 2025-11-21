package com.example.auraplay

import android.app.Application
import com.example.auraplay.data.AppDatabase

class AuraPlayApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    // Add an instance of your SettingsDataStore
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }
}