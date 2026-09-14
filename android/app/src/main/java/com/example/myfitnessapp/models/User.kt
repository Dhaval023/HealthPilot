package com.example.myfitnessapp.models

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val height: Int = 0,
    val weight: Double = 0.0,
    val age: Int = 0,
    val dob: String = "",
    val gender: String = "",
    val profileImageUrl: String = "",
    val goalType: String = "loss", // "loss" or "gain"
    val targetWeightLoss: Double = 0.0,
    val targetWeightGain: Double = 0.0,
    val targetWeight: Double = 0.0,
    val targetCalories: Int = 0,
    val targetSteps: Int = 0,
    val targetDistanceKm: Double = 0.0,
    val targetProtein: Int = 0,
    val targetCarbs: Int = 0,
    val targetFat: Int = 0,
    val targetDurationWeeks: Int = 12,
    val activityLevel: String = "MODERATE",
    val startWeight: Double = 0.0,
    val startDate: String = "",
    val reminders: List<Reminder> = emptyList(),
    val wellnessReminders: List<Reminder> = emptyList(),
    // New fields for Ideal Time scheduling
    val wakeUpTime: String = "07:00", // HH:mm
    val sleepTime: String = "22:00",  // HH:mm
    val workStartTime: String = "09:00",
    val workEndTime: String = "17:00",
    val workStyle: String = "Mostly Sitting", // Mostly Sitting, Mostly Standing, Mixed Activity, Heavy Physical Work, Driving, Home & Care
    val dailyScreenTime: Int = 0,
    val waterIntakeGoalMl: Int = 2000,
    val exerciseHabit: String = "None",
    val medicalRestrictions: String = "",
    val alarmTune: String = "default"
)
