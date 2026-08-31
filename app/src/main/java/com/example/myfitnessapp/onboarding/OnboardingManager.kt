package com.example.myfitnessapp.onboarding

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth

class OnboardingManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    private val userId: String? = FirebaseAuth.getInstance().currentUser?.uid

    fun shouldShowOnboarding(): Boolean {
        val key = getPrefKey() ?: return false
        return prefs.getBoolean(key, true)
    }

    fun setOnboardingShown() {
        val key = getPrefKey() ?: return
        prefs.edit().putBoolean(key, false).apply()
    }

    private fun getPrefKey(): String? {
        return userId?.let { "onboarding_shown_$it" } ?: "onboarding_shown_guest"
    }

    fun resetOnboarding() {
        val key = getPrefKey() ?: return
        prefs.edit().putBoolean(key, true).apply()
    }
}
