package com.example.myfitnessapp.onboarding

import android.view.View

data class OnboardingStep(
    val title: String,
    val description: String,
    val targetView: View? = null,
    val iconRes: Int? = null,
    val isFinalStep: Boolean = false,
    val paddingDp: Float = 16f // Default padding
)
