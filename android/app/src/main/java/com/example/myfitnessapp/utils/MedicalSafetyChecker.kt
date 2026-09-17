package com.example.myfitnessapp.utils

object MedicalSafetyChecker {

    private val EMERGENCY_KEYWORDS = listOf(
        "chest pain", "heart attack", "can't breathe", "difficulty breathing",
        "stroke", "numbness", "confusion", "unconscious", "seizure",
        "heavy bleeding", "suicide", "self harm", "poison", "overdose",
        "severe allergic reaction", "anaphylaxis", "choking"
    )

    /**
     * Checks if the user's input contains obvious emergency keywords.
     * This is a first-pass safety layer before sending to AI.
     */
    fun isPotentialEmergency(input: String): Boolean {
        val lowerInput = input.lowercase()
        return EMERGENCY_KEYWORDS.any { keyword ->
            lowerInput.contains(keyword)
        }
    }

    /**
     * Returns a list of symptoms that should trigger immediate emergency care.
     */
    fun getEmergencySymptoms(): List<String> {
        return listOf(
            "Severe difficulty breathing",
            "Severe chest pain",
            "Signs of stroke (face drooping, arm weakness, speech difficulty)",
            "Loss of consciousness",
            "Severe allergic reaction",
            "Uncontrolled heavy bleeding",
            "Sudden severe confusion",
            "Seizure"
        )
    }
}
