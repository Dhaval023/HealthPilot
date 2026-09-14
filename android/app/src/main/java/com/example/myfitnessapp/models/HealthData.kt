package com.example.myfitnessapp.models

data class HealthData(
    val heartRate: Int = 0,
    val steps: Int = 0,
    val calories: Int = 0,
    val distanceKm: Double = 0.0,
    val sleepDuration: String = "0h 0m",
    val spo2: Int = 0,
    val batteryLevel: Int = 0,
    val rssi: Int = -100
)
