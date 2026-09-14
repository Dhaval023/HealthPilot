package com.example.myfitnessapp.utils

import com.example.myfitnessapp.R

object ReminderIconUtils {

    fun getIconForType(type: String): Int {
        return when (type.lowercase().trim()) {
            "breakfast" -> R.drawable.ic_breakfast
            "lunch" -> R.drawable.ic_lunch
            "dinner" -> R.drawable.ic_dinner
            "snack", "snacks" -> R.drawable.ic_evening_snacks
            "water" -> R.drawable.ic_water
            "stand up", "stand" -> R.drawable.ic_walk
            "walk", "walking" -> R.drawable.ic_walk
            "stretch", "stretching", "leg stretch" -> R.drawable.healing
            "eye exercise", "eye relaxation" -> R.drawable.ic_chatbot
            "sit break", "rest" -> R.drawable.seat
            "deep breathing", "relaxation" -> R.drawable.ic_chatbot
            "nutrition" -> R.drawable.calories
            "safe break" -> R.drawable.ic_location
            "posture" -> R.drawable.healing
            "sleep preparation", "sleep" -> R.drawable.ic_sleep
            "exercise", "workout" -> R.drawable.ic_steps_styled
            else -> R.drawable.ic_breakfast
        }
    }
}
