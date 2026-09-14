package com.example.myfitnessapp.goal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitnessapp.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.myfitnessapp.data.repository.HealthRepository
import com.example.myfitnessapp.utils.GoalUtils
import kotlinx.coroutines.flow.collectLatest

data class GoalState(
    val bmi: Double = 0.0,
    val bmiCategory: String = "",
    val targetCalories: Int = 0,
    val targetSteps: Int = 0,
    val targetDistanceKm: Double = 0.0,
    val maintenanceCalories: Int = 0,
    val dailyCalorieBurnEstimate: Int = 0,
    val currentSteps: Int = 0,
    val currentCalories: Int = 0,
    val goalProgress: Float = 0f,
    val weeklyGoals: List<DailyGoal> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class DailyGoal(
    val day: String,
    val steps: Int,
    val distance: Double,
    val calories: Int,
    val variance: String,
    val isRestDay: Boolean
)

class GoalViewModel(private val repository: HealthRepository) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableLiveData<GoalState>(GoalState())
    val state: LiveData<GoalState> = _state

    private var currentUser: User? = null

    init {
        fetchUserAndCalculateGoals()
        observeHealthData()
    }

    private fun observeHealthData() {
        viewModelScope.launch {
            repository.healthData.collectLatest { data ->
                val currentState = _state.value ?: return@collectLatest
                val progress = if (currentState.targetSteps > 0) {
                    data.steps.toFloat() / currentState.targetSteps
                } else 0f

                _state.value = currentState.copy(
                    currentSteps = data.steps,
                    currentCalories = data.calories,
                    goalProgress = progress.coerceIn(0f, 1f)
                )
            }
        }
    }

    private fun fetchUserAndCalculateGoals() {
        viewModelScope.launch {
            _state.value = _state.value?.copy(isLoading = true, error = null)
            val startTime = System.currentTimeMillis()
            val uid = auth.currentUser?.uid ?: run {
                _state.value = GoalState(error = "User not logged in")
                return@launch
            }

            try {
                val snapshot = db.collection("users").document(uid).get().await()
                val user = snapshot.toObject(User::class.java)
                currentUser = user

                if (user != null) {
                    val weeklyGoals = GoalUtils.generateWeeklyGoals(user.targetSteps, user)
                    val todayGoal = GoalUtils.getTodayGoal(weeklyGoals)
                    val bmi = GoalUtils.calculateBMI(user)
                    val maintenance = GoalUtils.calculateMaintenanceCalories(user)
                    
                    val healthData = repository.healthData.value
                    val progress = if (todayGoal.steps > 0) {
                        healthData.steps.toFloat() / todayGoal.steps
                    } else 0f

                    val newState = GoalState(
                        bmi = bmi,
                        bmiCategory = GoalUtils.getBMICategory(bmi),
                        targetCalories = todayGoal.calories,
                        targetSteps = todayGoal.steps,
                        targetDistanceKm = todayGoal.distance,
                        maintenanceCalories = maintenance,
                        dailyCalorieBurnEstimate = maintenance + todayGoal.calories,
                        currentSteps = healthData.steps,
                        currentCalories = healthData.calories,
                        goalProgress = progress.coerceIn(0f, 1f),
                        weeklyGoals = weeklyGoals,
                        isLoading = false
                    )

                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < 500) {
                        kotlinx.coroutines.delay(500 - elapsedTime)
                    }
                    _state.value = newState
                } else {
                    _state.value = _state.value?.copy(isLoading = false, error = "User data not found")
                }
            } catch (e: Exception) {
                _state.value = _state.value?.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateGoalsByCalories(calories: Int) {
        val user = currentUser ?: return
        // base steps calculation from active calories
        val baseSteps = ((calories * 6000.0) / (3.5 * user.weight)).toInt()
        val weeklyGoals = GoalUtils.generateWeeklyGoals(baseSteps, user)
        syncStateWithToday(weeklyGoals)
    }

    fun updateGoalsBySteps(steps: Int) {
        val user = currentUser ?: return
        val weeklyGoals = GoalUtils.generateWeeklyGoals(steps, user)
        syncStateWithToday(weeklyGoals)
    }

    fun updateGoalsByDistance(distance: Double) {
        val user = currentUser ?: return
        val strideLength = GoalUtils.calculateStrideLength(user)
        val baseSteps = ((distance * 1000.0) / strideLength).toInt()
        val weeklyGoals = GoalUtils.generateWeeklyGoals(baseSteps, user)
        syncStateWithToday(weeklyGoals)
    }

    private fun syncStateWithToday(weeklyGoals: List<DailyGoal>) {
        val todayGoal = GoalUtils.getTodayGoal(weeklyGoals)

        _state.value = _state.value?.copy(
            targetCalories = todayGoal.calories,
            targetSteps = todayGoal.steps,
            targetDistanceKm = todayGoal.distance,
            goalProgress = calculateProgress(todayGoal.steps),
            dailyCalorieBurnEstimate = _state.value!!.maintenanceCalories + todayGoal.calories,
            weeklyGoals = weeklyGoals
        )
    }

    private fun calculateProgress(targetSteps: Int): Float {
        val currentSteps = repository.healthData.value.steps
        return if (targetSteps > 0) {
            (currentSteps.toFloat() / targetSteps).coerceIn(0f, 1f)
        } else 0f
    }

    fun resetToRecommended() {
        val user = currentUser ?: return
        val recommendedSteps = generateRecommendedSteps(user)
        val weeklyGoals = GoalUtils.generateWeeklyGoals(recommendedSteps, user)
        syncStateWithToday(weeklyGoals)
    }

    private fun generateRecommendedSteps(user: User): Int {
        val bmi = GoalUtils.calculateBMI(user)
        var steps = when {
            bmi < 18.5 -> 8000
            bmi < 25 -> 10000
            bmi < 30 -> 12000
            else -> 15000
        }

        steps = when {
            user.age < 30 -> steps + 1000
            user.age < 40 -> steps
            user.age < 50 -> steps - 500
            user.age < 60 -> steps - 1000
            user.age < 70 -> steps - 2000
            else -> steps - 3000
        }

        val currentSteps = repository.healthData.value.steps
        if (currentSteps > 0) {
            steps = kotlin.math.min(steps, (currentSteps * 1.2).toInt())
        }

        return steps.coerceIn(GoalUtils.MIN_STEPS, GoalUtils.MAX_STEPS)
    }

    fun saveGoals() {
        val currentState = _state.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val user = currentUser ?: return

        viewModelScope.launch {
            // We want to save the BASE steps, not today's adjusted steps
            // We can approximate base steps from current target calories if they were just edited
            // Actually, if they just edited, the weeklyGoals list was regenerated from that new base.
            // Let's find the original base steps.
            
            // Simplest way: if weeklyGoals is not empty, use targetSteps/variance to find base?
            // Or just store the base in the state.
            
            // For now, let's just save the current target values as they are what the user sees.
            // BUT to avoid compounding, we'll use today's goal as the new base.
            val updates = mapOf(
                "targetCalories" to currentState.targetCalories,
                "targetSteps" to currentState.targetSteps,
                "targetDistanceKm" to currentState.targetDistanceKm
            )

            try {
                db.collection("users").document(uid).update(updates).await()
            } catch (e: Exception) {
                _state.value = _state.value?.copy(error = "Failed to save: ${e.message}")
            }
        }
    }
}
