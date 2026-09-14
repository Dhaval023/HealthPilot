package com.example.myfitnessapp.utils

import com.example.myfitnessapp.goal.DailyGoal
import com.example.myfitnessapp.models.User
import java.util.*
import kotlin.math.min

object GoalUtils {
    const val MIN_STEPS = 2000
    const val MAX_STEPS = 30000

    fun calculateBMI(user: User): Double {
        if (user.height <= 0) return 0.0
        val heightM = user.height / 100.0
        return user.weight / (heightM * heightM)
    }

    fun getBMICategory(bmi: Double): String {
        return when {
            bmi < 16 -> "Severe Thinness"
            bmi < 17 -> "Moderate Thinness"
            bmi < 18.5 -> "Mild Thinness"
            bmi < 25 -> "Normal"
            bmi < 30 -> "Overweight"
            else -> "Obese"
        }
    }

    data class MacroTargets(
        val protein: Int,
        val carbs: Int,
        val fat: Int
    )

    enum class ActivityLevel(val factor: Double, val description: String) {
        SEDENTARY(1.2, "Sedentary (little or no exercise)"),
        LIGHT(1.375, "Lightly active (light exercise/sports 1-3 days/week)"),
        MODERATE(1.55, "Moderately active (moderate exercise/sports 3-5 days/week)"),
        ACTIVE(1.725, "Active (hard exercise/sports 6-7 days a week)"),
        VERY_ACTIVE(1.9, "Very active (very hard exercise/sports & physical job or 2x training)")
    }

    data class HealthPlan(
        val bmr: Double,
        val tdee: Double,
        val targetCalories: Int,
        val targetProtein: Int,
        val targetCarbs: Int,
        val targetFat: Int,
        val targetWater: Double,
        val targetSleep: Double,
        val calculationSteps: String
    )

    fun calculateHealthPlan(
        age: Int,
        gender: String,
        heightCm: Double,
        currentWeightKg: Double,
        targetWeightKg: Double,
        durationWeeks: Int,
        activityLevel: ActivityLevel
    ): HealthPlan {

        val isMale = gender.equals("male", ignoreCase = true)

        val bmr = if (isMale) {
            (10 * currentWeightKg) +
                    (6.25 * heightCm) -
                    (5 * age) + 5
        } else {
            (10 * currentWeightKg) +
                    (6.25 * heightCm) -
                    (5 * age) - 161
        }

        // 2. TDEE
        val tdee = bmr * activityLevel.factor

        // 3. Weight Goal Calculation
        val weightDifference = targetWeightKg - currentWeightKg

        val totalCaloriesNeeded = weightDifference * 7700.0

        val dailyCalorieAdjustment =
            if (durationWeeks > 0)
                totalCaloriesNeeded / (durationWeeks * 7.0)
            else
                0.0

        // REAL CALCULATED CALORIES
        val targetCalories =
            (tdee + dailyCalorieAdjustment).toInt()

        // 4. Protein
        val proteinGrams = when {
            targetWeightKg > currentWeightKg ->
                (targetWeightKg * 2.0).toInt()

            targetWeightKg < currentWeightKg ->
                (currentWeightKg * 2.2).toInt()

            else ->
                (currentWeightKg * 1.8).toInt()
        }

        // 5. Fat
        val fatGrams =
            (currentWeightKg * 1.0).toInt()

        // 6. Carbs
        val proteinCalories = proteinGrams * 4
        val fatCalories = fatGrams * 9

        val carbsGrams =
            ((targetCalories -
                    proteinCalories -
                    fatCalories) / 4.0)
                .toInt()
                .coerceAtLeast(0)

        // 7. Water
        val waterLiters =
            currentWeightKg * 0.04

        // 8. Sleep
        val sleepHours =
            if (targetWeightKg > currentWeightKg)
                8.5
            else
                8.0

        // Goal Analysis
        val weeklyChange =
            if (durationWeeks > 0)
                kotlin.math.abs(weightDifference) / durationWeeks
            else
                0.0

        val warning = when {
            weeklyChange > 1.0 ->
                "Goal is very aggressive and may not be achievable naturally."

            weeklyChange > 0.75 ->
                "Goal is aggressive."

            else ->
                "Goal is realistic."
        }

        val steps = buildString {

            appendLine("===== HEALTH PLAN CALCULATION =====")
            appendLine()

            appendLine("AGE: $age")
            appendLine("GENDER: $gender")
            appendLine("HEIGHT: $heightCm cm")
            appendLine("CURRENT WEIGHT: $currentWeightKg kg")
            appendLine("TARGET WEIGHT: $targetWeightKg kg")
            appendLine("DURATION: $durationWeeks weeks")
            appendLine()

            appendLine("1. BMR")
            appendLine(
                if (isMale)
                    "(10 × Weight) + (6.25 × Height) - (5 × Age) + 5"
                else
                    "(10 × Weight) + (6.25 × Height) - (5 × Age) - 161"
            )
            appendLine("BMR = ${String.format("%.2f", bmr)} kcal")
            appendLine()

            appendLine("2. TDEE")
            appendLine("TDEE = BMR × Activity Factor")
            appendLine(
                "${String.format("%.2f", bmr)} × ${activityLevel.factor}"
            )
            appendLine("TDEE = ${String.format("%.2f", tdee)} kcal")
            appendLine()

            appendLine("3. CALORIES")
            appendLine(
                "Weight Difference = $targetWeightKg - $currentWeightKg = $weightDifference kg"
            )

            appendLine(
                "Total Calories Needed = $weightDifference × 7700"
            )

            appendLine(
                "Total Calories Needed = ${String.format("%.0f", totalCaloriesNeeded)} kcal"
            )

            appendLine(
                "Daily Adjustment = ${String.format("%.0f", totalCaloriesNeeded)} ÷ (${durationWeeks * 7})"
            )

            appendLine(
                "Daily Adjustment = ${String.format("%.0f", dailyCalorieAdjustment)} kcal"
            )

            appendLine(
                "Target Calories = ${String.format("%.0f", tdee)} + ${String.format("%.0f", dailyCalorieAdjustment)}"
            )

            appendLine(
                "Target Calories = $targetCalories kcal"
            )

            appendLine()

            appendLine("4. PROTEIN")
            appendLine("$proteinGrams g")
            appendLine()

            appendLine("5. FAT")
            appendLine("$fatGrams g")
            appendLine()

            appendLine("6. CARBS")
            appendLine("$carbsGrams g")
            appendLine()

            appendLine("7. WATER")
            appendLine("${String.format("%.1f", waterLiters)} L")
            appendLine()

            appendLine("8. SLEEP")
            appendLine("$sleepHours hours")
            appendLine()

            appendLine("WARNING")
            appendLine(warning)
            appendLine()

            appendLine("===== DAILY TARGETS =====")
            appendLine("Calories : $targetCalories kcal")
            appendLine("Protein  : $proteinGrams g")
            appendLine("Carbs    : $carbsGrams g")
            appendLine("Fat      : $fatGrams g")
            appendLine("Water    : ${String.format("%.1f", waterLiters)} L")
            appendLine("Sleep    : $sleepHours hours")
        }

        return HealthPlan(
            bmr = bmr,
            tdee = tdee,
            targetCalories = targetCalories,
            targetProtein = proteinGrams,
            targetCarbs = carbsGrams,
            targetFat = fatGrams,
            targetWater = waterLiters,
            targetSleep = sleepHours,
            calculationSteps = steps
        )
    }

    fun calculateMacroTargets(calories: Int, goalType: String): MacroTargets {
        return if (goalType == "loss") {
            // High Protein, Moderate Carbs, Moderate Fat (30/40/30)
            MacroTargets(
                protein = (calories * 0.30 / 4).toInt(),
                carbs = (calories * 0.40 / 4).toInt(),
                fat = (calories * 0.30 / 9).toInt()
            )
        } else {
            // Moderate Protein, High Carbs, Low Fat (25/55/20)
            MacroTargets(
                protein = (calories * 0.25 / 4).toInt(),
                carbs = (calories * 0.55 / 4).toInt(),
                fat = (calories * 0.20 / 9).toInt()
            )
        }
    }

    fun calculateMaintenanceCalories(user: User): Int {
        if (user.height <= 0 || user.weight <= 0 || user.age <= 0) return 2000
        val bmr = if (user.gender.lowercase().contains("male")) {
            88.362 + (13.397 * user.weight) + (4.799 * user.height) - (5.677 * user.age)
        } else {
            447.593 + (9.247 * user.weight) + (3.098 * user.height) - (4.330 * user.age)
        }
        return (bmr * 1.2).toInt()
    }

    fun calculateStrideLength(user: User): Double {
        val strideFactor = if (user.gender.lowercase().contains("male")) 0.415 else 0.413
        return (user.height * strideFactor) / 100.0
    }

    fun calculateActiveCalories(steps: Int, weight: Double): Int {
        val met = 3.5 // Walking MET value
        val durationMinutes = steps / 100.0 // 100 steps/min average
        return (met * weight * (durationMinutes / 60.0)).toInt()
    }

    fun generateWeeklyGoals(baseSteps: Int, user: User): List<DailyGoal> {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        
        // Stable seed for the week
        val calendar = Calendar.getInstance()
        val weekSeed = user.uid.hashCode().toLong() + 
                      calendar.get(Calendar.WEEK_OF_YEAR) + 
                      calendar.get(Calendar.YEAR)
        
        val random = kotlin.random.Random(weekSeed)
        val strideLength = calculateStrideLength(user)

        return days.map { day ->
            val randomFactor = random.nextDouble(0.0, 0.4)
            var dailySteps = (baseSteps * (0.8 + randomFactor)).toInt()
            dailySteps = dailySteps.coerceIn(MIN_STEPS, MAX_STEPS)

            val variancePercent = (randomFactor - 0.2) * 100
            val varianceString = String.format("%+.0f%%", variancePercent)

            val isRestDay = day == "Sat" || day == "Sun"
            val finalSteps = if (isRestDay) {
                (dailySteps * 0.8).toInt().coerceIn(MIN_STEPS, MAX_STEPS)
            } else {
                dailySteps
            }

            DailyGoal(
                day = day,
                steps = finalSteps,
                distance = (finalSteps * strideLength) / 1000.0,
                calories = calculateActiveCalories(finalSteps, user.weight.toDouble()),
                variance = if (isRestDay) "Rest Day" else varianceString,
                isRestDay = isRestDay
            )
        }
    }

    fun getTodayGoal(weeklyGoals: List<DailyGoal>): DailyGoal {
        val currentDay = Calendar.getInstance().getDisplayName(
            Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US
        )
        return weeklyGoals.find { it.day == currentDay } ?: weeklyGoals[0]
    }
}
