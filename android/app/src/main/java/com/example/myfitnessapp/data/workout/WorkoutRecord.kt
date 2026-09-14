package com.example.myfitnessapp.data.workout

import com.google.firebase.firestore.DocumentId

data class WorkoutRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workoutType: String = "",
    val date: Long = 0, // Timestamp for the day (start of day)
    val startTime: Long = 0,
    val endTime: Long = 0,
    val duration: Long = 0, // in milliseconds
    val totalSteps: Int = 0,
    val totalDistance: Double = 0.0, // in km
    val caloriesBurned: Double = 0.0,
    val averageHeartRate: Int? = null
)
