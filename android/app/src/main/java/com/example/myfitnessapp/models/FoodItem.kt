package com.example.myfitnessapp.models

data class FoodItem(
    val id: Int = 0,
    val name: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val imageUrl: String = "",
    val category: String = ""
)
