package com.example.myfitnessapp.healthgoal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitnessapp.R
import com.example.myfitnessapp.models.FoodItem
import com.example.myfitnessapp.models.User
import com.example.myfitnessapp.models.DailyData
import com.example.myfitnessapp.models.LoggedFood
import com.example.myfitnessapp.data.repository.HealthRepository
import com.example.myfitnessapp.utils.GoalUtils
import com.example.myfitnessapp.models.Reminder
import com.example.myfitnessapp.utils.IdealTimeUtils
import com.example.myfitnessapp.utils.ReminderManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class HealthGoalViewModel(application: Application, private val repository: HealthRepository) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var reminderManager: ReminderManager? = null

    fun initReminderManager(context: android.content.Context) {
        if (reminderManager == null) {
            reminderManager = ReminderManager(context.applicationContext)
        }
    }

    private val _uiState = MutableStateFlow(HealthGoalUiState())
    val uiState: StateFlow<HealthGoalUiState> = _uiState.asStateFlow()

    private val _validationError = MutableSharedFlow<String>()
    val validationError = _validationError.asSharedFlow()

    private var userListener: ListenerRegistration? = null
    private var dailyDataListener: ListenerRegistration? = null
    private var isDailyDataLoaded = false

    private var recalculationJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var recentFoodsListener: ListenerRegistration? = null
    private val FOOD_API_BASE_URL = com.example.myfitnessapp.data.repository.ApiKeyConfig.FOOD_API_BASE_URL

    init {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            android.util.Log.d("HealthGoalVM", "User authenticated: $uid. Starting data sync.")
            startObserving(uid)
        } else {
            android.util.Log.d("HealthGoalVM", "No user authenticated.")
        }
    }

    private fun startObserving(uid: String) {
        fetchUserData(uid)
        observeDailyData(uid)
        observeRepositoryHealthData()
        observeRecentFoods(uid)
    }

    private fun observeRecentFoods(uid: String) {
        recentFoodsListener?.remove()
        recentFoodsListener = db.collection("users").document(uid)
            .collection("recent_foods")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val foods = snapshot?.documents?.mapNotNull { it.toObject(FoodItem::class.java) } ?: emptyList()
                _uiState.update { it.copy(recentFoods = foods) }

                if (searchJob == null || !searchJob!!.isActive) {
                    _uiState.update { state -> 
                        if (state.searchResults.isEmpty() && state.recentFoods.isNotEmpty()) {
                            state.copy(searchResults = state.recentFoods)
                        } else state
                    }
                }
            }
    }

    private fun fetchUserData(uid: String) {
        userListener?.remove()
        val startTime = System.currentTimeMillis()
        userListener = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("HealthGoalVM", "Error fetching user data", error)
                _uiState.update { it.copy(error = error.message, isLoading = false) }
                return@addSnapshotListener
            }
            _uiState.update { it.copy(error = null) }
            val user = snapshot?.toObject(User::class.java)
            user?.let {
                val weeklyGoals = GoalUtils.generateWeeklyGoals(it.targetSteps, it)
                val todayGoal = GoalUtils.getTodayGoal(weeklyGoals)
                
                _uiState.update { state ->
                    val isLoss = it.goalType.lowercase() != "gain"
                    val firestoreDuration = if (it.targetDurationWeeks > 0) it.targetDurationWeeks else state.durationWeeks
                    
                    // Choose the correct target weight from Firestore based on current goal type
                    val weightFromDb = if (isLoss) it.targetWeightLoss else it.targetWeightGain
                    val hasSetGoal = it.targetWeightLoss > 0 || it.targetWeightGain > 0
                    
                    // Only update reminders from DB if we don't have pending local changes to them
                    val hasPendingReminders = snapshot.metadata.hasPendingWrites()
                    
                    state.copy(
                        currentWeight = if (it.weight > 0) it.weight else state.currentWeight,
                        targetWeightLoss = it.targetWeightLoss,
                        targetWeightGain = it.targetWeightGain,
                        targetWeight = if (weightFromDb > 0) weightFromDb else 0.0,
                        isGoalSet = hasSetGoal,
                        targetCalories = if (it.targetCalories > 0) it.targetCalories else state.targetCalories,
                        targetSteps = todayGoal.steps,
                        targetDistanceKm = todayGoal.distance,
                        isWeightLoss = isLoss,
                        gender = if (it.gender.isNotEmpty()) it.gender else state.gender,
                        age = if (it.age > 0) it.age else state.age,
                        height = if (it.height > 0) it.height.toDouble() else state.height,
                        durationWeeks = firestoreDuration,
                        activityLevel = try { GoalUtils.ActivityLevel.valueOf(it.activityLevel) } catch (e: Exception) { GoalUtils.ActivityLevel.MODERATE },
                        startWeight = it.startWeight,
                        startDate = it.startDate,
                        reminders = if (hasPendingReminders) state.reminders else (if (it.reminders.isNotEmpty()) it.reminders else state.reminders),
                        wellnessReminders = if (hasPendingReminders) state.wellnessReminders else it.wellnessReminders,
                        alarmTune = if (it.alarmTune.isNotEmpty()) it.alarmTune else state.alarmTune,
                        assistantVoice = if (it.assistantVoice.isNotEmpty()) it.assistantVoice else state.assistantVoice
                    )
                }

                viewModelScope.launch {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < 500) kotlinx.coroutines.delay(500 - elapsedTime)
                    _uiState.update { it.copy(isLoading = false) }
                }
                if (snapshot?.metadata?.hasPendingWrites() == false) {
                    _uiState.value.reminders.forEach { reminderManager?.scheduleReminder(it, _uiState.value.alarmTune) }
                    _uiState.value.wellnessReminders.forEach { reminderManager?.scheduleReminder(it, _uiState.value.alarmTune) }
                }

                val current = _uiState.value
                if (current.startWeight <= 0 || current.startDate.isEmpty()) {
                    initializeStartParams(current.currentWeight)
                }

                recalculatePlan()
            }
        }
    }

    private fun initializeStartParams(weight: Double) {
        val uid = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val updates = hashMapOf<String, Any>(
            "startWeight" to weight,
            "startDate" to today
        )
        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                _uiState.update { it.copy(startWeight = weight, startDate = today) }
            }
    }

    private fun observeDailyData(uid: String) {
        dailyDataListener?.remove()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val startTime = System.currentTimeMillis()
        dailyDataListener = db.collection("users").document(uid)
            .collection("daily_data").document(today)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("HealthGoalVM", "Error observing daily data", error)
                    isDailyDataLoaded = true 
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                    return@addSnapshotListener
                }
                _uiState.update { it.copy(error = null) }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.toObject(DailyData::class.java)
                    if (data != null) {
                        android.util.Log.d("HealthGoalVM", "Daily data loaded for $today: ${data.caloriesConsumed} kcal")
                        _uiState.update { state ->
                            val strideLength = GoalUtils.calculateStrideLength(
                                User(height = state.height.toInt(), gender = state.gender)
                            )
                            state.copy(
                                caloriesConsumed = data.caloriesConsumed,
                                currentWater = data.waterIntakeL,
                                steps = if (data.steps > 0) data.steps else state.steps,
                                currentDistanceKm = (data.steps * strideLength) / 1000.0,
                                heartRate = if (data.heartRate > 0) data.heartRate else state.heartRate,
                                loggedFoods = data.loggedFoods,
                                proteinConsumed = data.proteinConsumed,
                                carbsConsumed = data.carbsConsumed,
                                fatConsumed = data.fatConsumed
                            )
                        }
                    }
                } else {
                    android.util.Log.d("HealthGoalVM", "No daily data found for $today, starting fresh.")
                }
                
                viewModelScope.launch {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < 500) kotlinx.coroutines.delay(500 - elapsedTime)
                    _uiState.update { it.copy(isLoading = false) }
                    isDailyDataLoaded = true 
                }
            }
    }

    private fun observeRepositoryHealthData() {
        viewModelScope.launch {
            repository.healthData.collectLatest { healthData ->
                if (healthData.steps > 0 || healthData.heartRate > 0) {
                    val strideLength = GoalUtils.calculateStrideLength(
                        User(height = _uiState.value.height.toInt(), gender = _uiState.value.gender)
                    )
                    val distance = (healthData.steps * strideLength) / 1000.0

                    _uiState.update { it.copy(
                        steps = if (healthData.steps > it.steps) healthData.steps else it.steps,
                        currentDistanceKm = distance,
                        heartRate = if (healthData.heartRate > 0) healthData.heartRate else it.heartRate
                    ) }
                    syncHealthDataToDb()
                }
            }
        }
    }

    private fun syncHealthDataToDb() {
        if (!isDailyDataLoaded) {
            android.util.Log.d("HealthGoalVM", "Sync skipped: Daily data not yet loaded from DB")
            return
        }

        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val state = _uiState.value

            val dailyData = DailyData(
                date = today,
                steps = state.steps,
                heartRate = state.heartRate,
                caloriesConsumed = state.caloriesConsumed,
                proteinConsumed = state.proteinConsumed,
                carbsConsumed = state.carbsConsumed,
                fatConsumed = state.fatConsumed,
                waterIntakeL = state.currentWater,
                weight = state.currentWeight,
                loggedFoods = state.loggedFoods
            )

            db.collection("users").document(uid)
                .collection("daily_data").document(today)
                .set(dailyData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    android.util.Log.d("HealthGoalVM", "Daily data synced successfully for $today")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("HealthGoalVM", "Failed to sync daily data", e)
                }
        }
    }

    private var allFoodsCache: List<FoodItem> = emptyList()

    fun searchFood(query: String) {
        searchJob?.cancel()
        if (query.isEmpty()) {
            _uiState.update { it.copy(searchResults = it.recentFoods, isFoodLoading = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isFoodLoading = true) }
            // Debounce to avoid excessive API calls while typing
            kotlinx.coroutines.delay(300)

            try {
                val foodsFromApi = withContext(Dispatchers.IO) {
                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
                    val url = URL("$FOOD_API_BASE_URL/api/foods/search/$encodedQuery")
                    val connection = url.openConnection()
                    val response = connection.getInputStream().bufferedReader().readText()
                    parseJsonToFoodList(response)
                }

                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                val filtered = foodsFromApi
                    .groupBy { it.name.lowercase(Locale.US).trim() }
                    .map { entry ->
                        val itemsWithName = entry.value
                        if (itemsWithName.size <= 1) {
                            itemsWithName.first()
                        } else {
                            itemsWithName.minByOrNull { item ->
                                val categoryHour = getCategoryPreferredHour(item.category)
                                Math.abs(currentHour - categoryHour)
                            } ?: itemsWithName.first()
                        }
                    }

                _uiState.update { it.copy(searchResults = filtered, isFoodLoading = false) }
            } catch (e: Exception) {
                android.util.Log.e("HealthGoalVM", "Search failed", e)
                _uiState.update { it.copy(isFoodLoading = false) }
            }
        }
    }

    private fun getCategoryPreferredHour(category: String): Int {
        return when (category.lowercase(Locale.US)) {
            "breakfast" -> 8
            "lunch" -> 13
            "afternoon snacks", "evening snacks", "snacks", "evening snack", "afternoon snack" -> 17
            "dinner" -> 20
            else -> 12 // Default to midday
        }
    }

    private suspend fun fetchAllFoods() {
        try {
            _uiState.update { it.copy(isFoodLoading = true) }
            allFoodsCache = withContext(Dispatchers.IO) {
                val url = URL("$FOOD_API_BASE_URL/api/foods")
                val connection = url.openConnection()
                val response = connection.getInputStream().bufferedReader().readText()
                val foods = parseJsonToFoodList(response)
                
                val categories = foods.map { it.category }.distinct()
                _uiState.update { it.copy(categories = categories) }
                
                // If no recent foods, populate with some defaults
                if (_uiState.value.recentFoods.isEmpty() && foods.isNotEmpty()) {
                    val defaults = foods.shuffled().take(10)
                    _uiState.update { it.copy(recentFoods = defaults, searchResults = defaults) }
                }
                
                foods
            }
            _uiState.update { it.copy(isFoodLoading = false) }
        } catch (e: Exception) {
            android.util.Log.e("HealthGoalVM", "Error fetching all foods", e)
            allFoodsCache = emptyList()
            _uiState.update { it.copy(isFoodLoading = false) }
        }
    }

    private fun parseJsonToFoodList(response: String): List<FoodItem> {
        val jsonArray = JSONArray(response)
        val foods = mutableListOf<FoodItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            foods.add(
                FoodItem(
                    id = obj.optInt("id", 0),
                    name = obj.optString("name", ""),
                    calories = obj.optInt("calories", 0),
                    protein = obj.optDouble("protein", 0.0),
                    carbs = obj.optDouble("carbs", 0.0),
                    fat = obj.optDouble("fat", 0.0),
                    imageUrl = obj.optString("imageUrl", ""),
                    category = obj.optString("category", "Other")
                )
            )
        }
        return foods
    }

    fun fetchCategories() {
        viewModelScope.launch {
            if (allFoodsCache.isEmpty()) {
                fetchAllFoods()
            }
        }
    }

    fun filterByCategory(category: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFoodLoading = true) }
            try {
                val filtered = withContext(Dispatchers.IO) {
                    // Use %20 instead of + for spaces to be more compatible with standard API routers
                    val encodedCategory = java.net.URLEncoder.encode(category, "UTF-8").replace("+", "%20")
                    val url = URL("$FOOD_API_BASE_URL/api/foods/category/$encodedCategory")
                    val connection = url.openConnection()
                    val response = connection.getInputStream().bufferedReader().readText()
                    parseJsonToFoodList(response)
                }
                _uiState.update { it.copy(searchResults = filtered, isFoodLoading = false) }
            } catch (e: Exception) {
                android.util.Log.e("HealthGoalVM", "Category filter failed for $category", e)
                _uiState.update { it.copy(isFoodLoading = false) }
            }
        }
    }

    fun addFoodToDaily(food: FoodItem, quantity: Double) {
        saveToRecent(food)
        viewModelScope.launch {
            val logged = LoggedFood(
                name = food.name,
                calories = (food.calories * quantity).toInt(),
                protein = food.protein * quantity,
                carbs = food.carbs * quantity,
                fat = food.fat * quantity,
                quantity = quantity,
                imageUrl = food.imageUrl
            )
            _uiState.update { state ->
                val newList = state.loggedFoods + logged
                state.copy(
                    loggedFoods = newList,
                    caloriesConsumed = newList.sumOf { it.calories },
                    proteinConsumed = newList.sumOf { it.protein },
                    carbsConsumed = newList.sumOf { it.carbs },
                    fatConsumed = newList.sumOf { it.fat }
                )
            }
            syncHealthDataToDb()
        }
    }

    private fun saveToRecent(food: FoodItem) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val foodMap = hashMapOf(
                "id" to food.id,
                "name" to food.name,
                "calories" to food.calories,
                "protein" to food.protein,
                "carbs" to food.carbs,
                "fat" to food.fat,
                "imageUrl" to food.imageUrl,
                "category" to food.category,
                "timestamp" to System.currentTimeMillis()
            )
            // Use name as ID to avoid duplicates in recents, or a combination
            val docId = food.name.lowercase().replace(" ", "_")
            db.collection("users").document(uid)
                .collection("recent_foods").document(docId)
                .set(foodMap, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    fun removeFood(loggedFood: LoggedFood) {
        viewModelScope.launch {
            _uiState.update { state ->
                val newList = state.loggedFoods.filter { it.id != loggedFood.id }
                state.copy(
                    loggedFoods = newList,
                    caloriesConsumed = newList.sumOf { it.calories },
                    proteinConsumed = newList.sumOf { it.protein },
                    carbsConsumed = newList.sumOf { it.carbs },
                    fatConsumed = newList.sumOf { it.fat }
                )
            }
            syncHealthDataToDb()
        }
    }

    fun saveGoals(targetWeight: Double) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val currentState = _uiState.value
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            if (targetWeight <= 0) {
                _validationError.emit("Please set a valid target weight")
                return@launch
            }

            if (currentState.isWeightLoss && targetWeight >= currentState.currentWeight) {
                _validationError.emit("Target weight must be less than current weight for Weight Loss")
                return@launch
            }
            if (!currentState.isWeightLoss && targetWeight <= currentState.currentWeight) {
                _validationError.emit("Target weight must be greater than current weight for Weight Gain")
                return@launch
            }

            // Update specific target weight fields based on goal type
            _uiState.update { 
                if (currentState.isWeightLoss) it.copy(targetWeightLoss = targetWeight, targetWeight = targetWeight, isGoalSet = true, startWeight = currentState.currentWeight, startDate = today)
                else it.copy(targetWeightGain = targetWeight, targetWeight = targetWeight, isGoalSet = true, startWeight = currentState.currentWeight, startDate = today)
            }
            
            val updates = hashMapOf<String, Any>(
                "targetWeightLoss" to _uiState.value.targetWeightLoss,
                "targetWeightGain" to _uiState.value.targetWeightGain,
                "targetWeight" to targetWeight,
                "startWeight" to currentState.currentWeight,
                "startDate" to today,
                "targetCalories" to currentState.targetCalories,
                "targetSteps" to currentState.targetSteps,
                "targetProtein" to currentState.targetProtein,
                "targetCarbs" to currentState.targetCarbs,
                "targetFat" to currentState.targetFat,
                "targetDurationWeeks" to currentState.durationWeeks,
                "activityLevel" to currentState.activityLevel.name,
                "goalType" to if (currentState.isWeightLoss) "loss" else "gain"
            )
            
            try {
                db.collection("users").document(uid).update(updates)
                    .addOnSuccessListener {
                        android.util.Log.d("HealthGoalVM", "User goals updated successfully")
                    }
                
                // Force sync daily data when user manually saves goals
                forceSyncDailyData()
            } catch (e: Exception) {
                android.util.Log.e("HealthGoalVM", "Error updating user goals", e)
                db.collection("users").document(uid).set(updates, com.google.firebase.firestore.SetOptions.merge())
                forceSyncDailyData()
            }
        }
    }

    private fun forceSyncDailyData() {
        // We set this to true because the user is performing an action, 
        // so we trust the current state even if the initial load didn't finish.
        isDailyDataLoaded = true 
        syncHealthDataToDb()
    }

    fun setGoalType(isWeightLoss: Boolean) {
        val currentState = _uiState.value
        if (currentState.isWeightLoss == isWeightLoss) return

        _uiState.update { 
            val newTarget = if (isWeightLoss) it.targetWeightLoss else it.targetWeightGain
            it.copy(isWeightLoss = isWeightLoss, targetWeight = newTarget) 
        }
        
        // Also update Firestore goalType so it's remembered
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).update("goalType", if (isWeightLoss) "loss" else "gain")
        }
        
        recalculatePlan()
    }

    fun updateDuration(weeks: Int) {
        _uiState.update { it.copy(durationWeeks = weeks) }
        recalculatePlan()
    }

    fun updateTargetWeight(weight: Double) {
        val currentState = _uiState.value
        if (weight > 0) {
            if (currentState.isWeightLoss && weight >= currentState.currentWeight) {
                // We don't block typing but we don't recalculate invalid plans
                return
            }
            if (!currentState.isWeightLoss && weight <= currentState.currentWeight) {
                return
            }
        }
        _uiState.update { 
            val isGoalNowSet = it.isGoalSet || weight > 0
            if (currentState.isWeightLoss) it.copy(targetWeightLoss = weight, targetWeight = weight, isGoalSet = isGoalNowSet)
            else it.copy(targetWeightGain = weight, targetWeight = weight, isGoalSet = isGoalNowSet)
        }
        recalculatePlan()
    }

    private fun recalculatePlan() {
        recalculationJob?.cancel()
        recalculationJob = viewModelScope.launch {
            _uiState.update { it.copy(isCalculating = true) }
            
            // Wait for user to finish typing
            kotlinx.coroutines.delay(800)
            
            val state = _uiState.value
            
            if (state.targetWeight <= 0) {
                _uiState.update { it.copy(isCalculating = false) }
                return@launch
            }
            
            // Use defaults if user data is missing to ensure calculation always runs
            val calcAge = if (state.age > 0) state.age else 25
            val calcHeight = if (state.height > 0) state.height else 175.0
            val calcWeight = if (state.currentWeight > 0) state.currentWeight else 80.0
            val calcDuration = if (state.durationWeeks > 0) state.durationWeeks else 12

            val plan = GoalUtils.calculateHealthPlan(
                calcAge,
                state.gender,
                calcHeight,
                calcWeight,
                state.targetWeight,
                calcDuration,
                state.activityLevel
            )
            
            _uiState.update { it.copy(
                isCalculating = false,
                targetCalories = plan.targetCalories,
                targetProtein = plan.targetProtein,
                targetCarbs = plan.targetCarbs,
                targetFat = plan.targetFat,
                targetWaterL = plan.targetWater,
                targetSleepHours = plan.targetSleep.toInt()
            ) }
            
            android.util.Log.d("HealthGoalVM", "Recalculated Plan: ${plan.targetCalories} kcal for $calcDuration weeks")
        }
    }

    fun calculateAndSetHealthPlan(
        age: Int,
        gender: String,
        heightCm: Double,
        currentWeightKg: Double,
        targetWeightKg: Double,
        durationWeeks: Int,
        activityLevel: GoalUtils.ActivityLevel
    ) {
        val plan = GoalUtils.calculateHealthPlan(
            age, gender, heightCm, currentWeightKg, targetWeightKg, durationWeeks, activityLevel
        )
        
        _uiState.update { state ->
            state.copy(
                targetWeight = targetWeightKg,
                targetCalories = plan.targetCalories,
                targetProtein = plan.targetProtein,
                targetCarbs = plan.targetCarbs,
                targetFat = plan.targetFat,
                targetWaterL = plan.targetWater,
                targetSleepHours = plan.targetSleep.toInt(),
                isWeightLoss = targetWeightKg < currentWeightKg
            )
        }
        
        android.util.Log.d("HealthGoalVM", "Calculated Health Plan: \n${plan.calculationSteps}")
    }

    fun addWater(amountL: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentWater = it.currentWater + amountL) }
            syncHealthDataToDb()
        }
    }

    fun updateCurrentWeight(weight: Double) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            
            _uiState.update { it.copy(currentWeight = weight) }
            
            // 1. Update User Profile
            db.collection("users").document(uid).update("weight", weight)
            
            // 2. Update Daily Data
            syncHealthDataToDb()
            
            // 3. Add to Weight History Collection
            val log = hashMapOf(
                "weight" to weight,
                "date" to today,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("users").document(uid)
                .collection("weight_history").document(today)
                .set(log)
            
            recalculatePlan()
        }
    }

    fun updateStartWeight(weight: Double) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            _uiState.update { it.copy(startWeight = weight) }
            db.collection("users").document(uid).update("startWeight", weight)
        }
    }

    fun addReminder(type: String, time: String) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            
            // Localize default message
            val messageResId = when(type.lowercase()) {
                "breakfast" -> R.string.breakfast
                "lunch" -> R.string.lunch
                "snack" -> R.string.snacks
                "dinner" -> R.string.dinner
                else -> 0
            }
            val localizedType = if (messageResId != 0) getApplication<Application>().getString(messageResId) else type
            
            val newReminder = Reminder(
                id = UUID.randomUUID().toString(),
                type = localizedType,
                message = "Time for your $localizedType!",
                time = time,
                isEnabled = true
            )
            val updatedReminders = _uiState.value.reminders + newReminder
            _uiState.update { it.copy(reminders = updatedReminders) }
            reminderManager?.scheduleReminder(newReminder, _uiState.value.alarmTune)
            updateRemindersInDb(updatedReminders)
        }
    }

    fun removeReminder(reminderId: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            
            // Check standard reminders
            val reminderToRemove = currentState.reminders.find { it.id == reminderId }
            if (reminderToRemove != null) {
                reminderManager?.cancelReminder(reminderToRemove)
                val updated = currentState.reminders.filter { it.id != reminderId }
                _uiState.update { it.copy(reminders = updated) }
                updateRemindersInDb(updated)
                return@launch
            }

            // Check wellness reminders
            val wellnessToRemove = currentState.wellnessReminders.find { it.id == reminderId }
            if (wellnessToRemove != null) {
                reminderManager?.cancelReminder(wellnessToRemove)
                val updated = currentState.wellnessReminders.filter { it.id != reminderId }
                _uiState.update { it.copy(wellnessReminders = updated) }
                updateWellnessRemindersInDb(updated)
            }
        }
    }

    fun removeWellnessReminder(reminderId: String) {
        removeReminder(reminderId)
    }

    fun toggleReminder(reminderId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            
            // 1. Try to find in standard reminders
            val reminderInStd = currentState.reminders.find { it.id == reminderId }
            if (reminderInStd != null) {
                if (reminderInStd.isEnabled == isEnabled) return@launch
                val updated = currentState.reminders.map {
                    if (it.id == reminderId) it.copy(isEnabled = isEnabled) else it
                }
                _uiState.update { it.copy(reminders = updated) }
                reminderManager?.scheduleReminder(reminderInStd.copy(isEnabled = isEnabled), _uiState.value.alarmTune)
                updateRemindersInDb(updated)
                return@launch
            }

            // 2. Try to find in wellness reminders
            val reminderInWellness = currentState.wellnessReminders.find { it.id == reminderId }
            if (reminderInWellness != null) {
                if (reminderInWellness.isEnabled == isEnabled) return@launch
                val updated = currentState.wellnessReminders.map {
                    if (it.id == reminderId) it.copy(isEnabled = isEnabled) else it
                }
                _uiState.update { it.copy(wellnessReminders = updated) }
                reminderManager?.scheduleReminder(reminderInWellness.copy(isEnabled = isEnabled), _uiState.value.alarmTune)
                updateWellnessRemindersInDb(updated)
            }
        }
    }

    fun toggleWellnessReminder(reminderId: String, isEnabled: Boolean) {
        // Just delegate to the unified toggle function
        toggleReminder(reminderId, isEnabled)
    }

    fun updateReminderTime(reminderId: String, newTime: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            
            // Check standard reminders
            val updatedReminders = currentState.reminders.map {
                if (it.id == reminderId) it.copy(time = newTime) else it
            }
            if (updatedReminders != currentState.reminders) {
                _uiState.update { it.copy(reminders = updatedReminders) }
                updatedReminders.find { it.id == reminderId }?.let { reminderManager?.scheduleReminder(it, _uiState.value.alarmTune) }
                updateRemindersInDb(updatedReminders)
                return@launch
            }

            // Check wellness reminders
            val updatedWellness = currentState.wellnessReminders.map {
                if (it.id == reminderId) it.copy(time = newTime) else it
            }
            if (updatedWellness != currentState.wellnessReminders) {
                _uiState.update { it.copy(wellnessReminders = updatedWellness) }
                updatedWellness.find { it.id == reminderId }?.let { reminderManager?.scheduleReminder(it, _uiState.value.alarmTune) }
                updateWellnessRemindersInDb(updatedWellness)
            }
        }
    }

    fun updateWellnessReminderTime(reminderId: String, newTime: String) {
        updateReminderTime(reminderId, newTime)
    }

    fun updateAlarmTune(tune: String) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            _uiState.update { it.copy(alarmTune = tune) }
            db.collection("users").document(uid).update("alarmTune", tune)
            
            // Re-schedule all reminders to apply the new sound immediately
            _uiState.value.reminders.forEach { reminderManager?.scheduleReminder(it, tune) }
            _uiState.value.wellnessReminders.forEach { reminderManager?.scheduleReminder(it, tune) }
        }
    }

    fun updateAssistantVoice(voice: String) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            _uiState.update { it.copy(assistantVoice = voice) }
            db.collection("users").document(uid).update("assistantVoice", voice)
        }
    }

    private fun updateRemindersInDb(reminders: List<Reminder>) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .set(mapOf("reminders" to reminders), com.google.firebase.firestore.SetOptions.merge())
    }

    private fun updateWellnessRemindersInDb(reminders: List<Reminder>) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .set(mapOf("wellnessReminders" to reminders), com.google.firebase.firestore.SetOptions.merge())
    }

    fun generateIdealSchedule() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                val snapshot = db.collection("users").document(uid).get().await()
                val user = snapshot.toObject(User::class.java) ?: return@launch
                
                // IdealTimeUtils should generate wellness tasks
                val idealReminders = IdealTimeUtils.generateIdealSchedule(user)
                _uiState.update { it.copy(wellnessReminders = idealReminders) }
                
                idealReminders.forEach { reminderManager?.scheduleReminder(it) }
                updateWellnessRemindersInDb(idealReminders)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setVisionLoading(loading: Boolean) {
        _uiState.update { it.copy(isVisionLoading = loading) }
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        dailyDataListener?.remove()
        recentFoodsListener?.remove()
    }

    data class HealthGoalUiState(
        val isWeightLoss: Boolean = true,
        val isCalculating: Boolean = false,
        val isFoodLoading: Boolean = false,
        val isVisionLoading: Boolean = false,
        val isGoalSet: Boolean = false,
        val currentWeight: Double = 80.0,
        val startWeight: Double = 0.0,
        val startDate: String = "",
        val targetWeight: Double = 0.0,
        val targetWeightLoss: Double = 0.0,
        val targetWeightGain: Double = 0.0,
        val durationWeeks: Int = 12,
        val age: Int = 25,
        val height: Double = 175.0,
        val activityLevel: GoalUtils.ActivityLevel = GoalUtils.ActivityLevel.MODERATE,
        val targetCalories: Int = 1800,
        val targetSteps: Int = 12000,
        val targetDistanceKm: Double = 8.5,
        val targetWaterL: Double = 3.0,
        val targetSleepHours: Int = 8,
        val targetProtein: Int = 120,
        val targetCarbs: Int = 250,
        val targetFat: Int = 80,
        val currentWater: Double = 0.0,
        val caloriesConsumed: Int = 0,
        val proteinConsumed: Double = 0.0,
        val carbsConsumed: Double = 0.0,
        val fatConsumed: Double = 0.0,
        val steps: Int = 0,
        val currentDistanceKm: Double = 0.0,
        val heartRate: Int = 0,
        val foodResults: List<FoodItem> = emptyList(),
        val searchResults: List<FoodItem> = emptyList(),
        val recentFoods: List<FoodItem> = emptyList(),
        val categories: List<String> = emptyList(),
        val loggedFoods: List<LoggedFood> = emptyList(),
        val reminders: List<Reminder> = emptyList(), // Initialize empty to fetch from localized defaults
        val wellnessReminders: List<Reminder> = emptyList(),
        val alarmTune: String = "default",
        val assistantVoice: String = "Arya",
        val gender: String = "Male",
        val error: String? = null,
        val isLoading: Boolean = true
    ) {
        val weightProgress: Double
            get() {
                if (startWeight <= 0 || targetWeight == startWeight || targetWeight <= 0) return 0.0
                
                val totalToChange = Math.abs(targetWeight - startWeight)
                val currentChanged = if (isWeightLoss) {
                    (startWeight - currentWeight)
                } else {
                    (currentWeight - startWeight)
                }

                if (totalToChange <= 0) return 0.0
                return ((currentChanged / totalToChange) * 100.0).coerceIn(0.0, 100.0)
            }

        val timeProgressPercent: Int
            get() {
                if (startDate.isEmpty() || durationWeeks <= 0) return 0
                return try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val start = sdf.parse(startDate)?.time ?: return 0
                    val totalMillis = durationWeeks * 7L * 24 * 60 * 60 * 1000
                    val elapsed = System.currentTimeMillis() - start
                    ((elapsed.toDouble() / totalMillis) * 100).toInt().coerceIn(0, 100)
                } catch (e: Exception) { 0 }
            }

        val status: String
            get() {
                val weightP = weightProgress.toInt()
                if (weightP >= timeProgressPercent) return "Ahead of Schedule"
                if (weightP >= timeProgressPercent - 10) return "On Track"
                return "Behind Schedule"
            }
        
        val statusColor: String // Hex color
            get() = when(status) {
                "Ahead of Schedule" -> "#4CAF50" // Green
                "On Track" -> "#2196F3" // Blue
                else -> "#F44336" // Red
            }
    }
}
