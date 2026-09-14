package com.example.myfitnessapp.models

import java.util.UUID

data class DailyData(
    val date: String = "", 
    val steps: Int = 0,
    val heartRate: Int = 0,
    val caloriesConsumed: Int = 0,
    val proteinConsumed: Double = 0.0,
    val carbsConsumed: Double = 0.0,
    val fatConsumed: Double = 0.0,
    val waterIntakeL: Double = 0.0,
    val sleepHours: Double = 0.0,
    val weight: Double = 0.0,
    val loggedFoods: List<LoggedFood> = emptyList()
)

data class LoggedFood(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val quantity: Double = 1.0,
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
