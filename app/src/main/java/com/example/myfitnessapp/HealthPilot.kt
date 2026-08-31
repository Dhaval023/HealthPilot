package com.example.myfitnessapp

import android.app.Application
import com.example.myfitnessapp.ble.BleManager
import com.example.myfitnessapp.data.repository.HealthRepository
import com.example.myfitnessapp.utils.NetworkObserver

class HealthPilot : Application() {
    
    lateinit var bleManager: BleManager
    lateinit var repository: HealthRepository
    lateinit var networkObserver: NetworkObserver

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
        repository = HealthRepository(bleManager)
        networkObserver = NetworkObserver(this)
    }
}
