package com.example.myfitnessapp.utils

import android.content.Context
import com.example.myfitnessapp.R

object ReminderLocalizationUtils {

    fun getLocalizedType(context: Context, type: String): String {
        val resId = when (type.lowercase()) {
            "water" -> R.string.type_water
            "stand up" -> R.string.type_stand_up
            "walk" -> R.string.type_walk
            "eye exercise" -> R.string.type_eye_exercise
            "stretch" -> R.string.type_stretch
            "sit break" -> R.string.type_sit_break
            "leg stretch" -> R.string.type_leg_stretch
            "foot relaxation" -> R.string.type_foot_relaxation
            "deep breathing" -> R.string.type_deep_breathing
            "rest" -> R.string.type_rest
            "nutrition" -> R.string.type_nutrition
            "safe break" -> R.string.type_safe_break
            "eye relaxation" -> R.string.type_eye_relaxation
            "posture" -> R.string.type_posture
            "relaxation" -> R.string.type_relaxation
            "stretching" -> R.string.type_stretching
            "exercise" -> R.string.type_exercise
            "sleep preparation" -> R.string.type_sleep_prep
            "breakfast" -> R.string.breakfast
            "lunch" -> R.string.lunch
            "dinner" -> R.string.dinner
            "snacks", "snack" -> R.string.snacks
            else -> null
        }
        return resId?.let { context.getString(it) } ?: type
    }

    fun getLocalizedMessage(context: Context, type: String, defaultMessage: String): String {
        // If message is empty, we usually use the type-based default in ReminderManager
        // But here we provide the localized version of the ideal schedule messages
        val resId = when (type.lowercase()) {
            "water" -> R.string.reminder_water_msg
            "stand up" -> R.string.reminder_stand_up_msg
            "walk" -> R.string.reminder_walk_msg
            "eye exercise" -> R.string.reminder_eye_exercise_msg
            "stretch" -> R.string.reminder_stretch_msg
            "sit break" -> R.string.reminder_sit_break_msg
            "leg stretch" -> R.string.reminder_leg_stretch_msg
            "foot relaxation" -> R.string.reminder_foot_relaxation_msg
            "deep breathing" -> R.string.reminder_deep_breathing_msg
            "rest" -> R.string.reminder_rest_msg
            "nutrition" -> R.string.reminder_nutrition_msg
            "safe break" -> R.string.reminder_safe_break_msg
            "eye relaxation" -> R.string.reminder_eye_relaxation_msg
            "posture" -> R.string.reminder_posture_msg
            "relaxation" -> R.string.reminder_relaxation_msg
            "stretching" -> R.string.reminder_stretching_msg
            "exercise" -> R.string.reminder_exercise_msg
            "sleep preparation" -> R.string.reminder_sleep_prep_msg
            else -> null
        }
        if (resId == null && (defaultMessage.isEmpty() || defaultMessage.contains("Time for your", ignoreCase = true))) {
            return context.getString(R.string.reminder_message_format, getLocalizedType(context, type))
        }
        return resId?.let { context.getString(it) } ?: defaultMessage
    }
}
