package com.example.myfitnessapp.models

data class Reminder(
    val id: String = "",
    val type: String = "", // e.g., "Breakfast", "Lunch", "Water"
    val message: String = "",
    val time: String = "08:00", // 24h format HH:mm
    val isEnabled: Boolean = true
)
