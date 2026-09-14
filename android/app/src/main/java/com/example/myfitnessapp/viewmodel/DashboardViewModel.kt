package com.example.myfitnessapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.ble.BleManager
import com.example.myfitnessapp.models.BleDevice
import com.example.myfitnessapp.models.HealthData
import com.example.myfitnessapp.models.DailyData
import com.example.myfitnessapp.data.repository.HealthRepository
import com.example.myfitnessapp.models.User
import com.example.myfitnessapp.utils.GoalUtils
import com.example.myfitnessapp.utils.IdealTimeUtils
import com.example.myfitnessapp.utils.ReminderManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel(
    application: Application,
    private val repository: HealthRepository
) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val networkObserver = (application as HealthPilot).networkObserver
    private var reminderManager: ReminderManager? = null

    private fun checkNetwork(): Boolean {
        return networkObserver.isNetworkAvailable()
    }

    fun initReminderManager(context: android.content.Context) {
        if (reminderManager == null) {
            reminderManager = ReminderManager(context.applicationContext)
        }
    }

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _dailyData = MutableStateFlow(DailyData())
    val dailyData: StateFlow<DailyData> = _dailyData.asStateFlow()

    private val _todayTargetSteps = MutableStateFlow(10000)
    val todayTargetSteps: StateFlow<Int> = _todayTargetSteps.asStateFlow()

    private val _todayTargetCalories = MutableStateFlow(2000)
    val todayTargetCalories: StateFlow<Int> = _todayTargetCalories.asStateFlow()

    private val _todayTargetBurnCalories = MutableStateFlow(500)
    val todayTargetBurnCalories: StateFlow<Int> = _todayTargetBurnCalories.asStateFlow()

    private val _todayTargetDistance = MutableStateFlow(7.0)
    val todayTargetDistance: StateFlow<Double> = _todayTargetDistance.asStateFlow()

    private val _targetProtein = MutableStateFlow(120)
    val targetProtein: StateFlow<Int> = _targetProtein.asStateFlow()

    private val _targetCarbs = MutableStateFlow(250)
    val targetCarbs: StateFlow<Int> = _targetCarbs.asStateFlow()

    private val _targetFat = MutableStateFlow(80)
    val targetFat: StateFlow<Int> = _targetFat.asStateFlow()

    val healthData: StateFlow<HealthData> = repository.healthData
    val connectionState: StateFlow<BleManager.ConnectionState> = repository.connectionState
    val foundDevices: StateFlow<List<BleDevice>> = repository.foundDevices
    val connectedDeviceName: StateFlow<String?> = repository.connectedDeviceName
    val isWatch: StateFlow<Boolean> = repository.isWatch
    
    val connectedDeviceAddress: String?
        get() = repository.connectedDeviceAddress

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchUser()
        fetchDailyData()
        repository.autoConnect()
    }

    private fun fetchUser() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            _isLoading.value = true
            val startTime = System.currentTimeMillis()
            
            db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                
                val userObj = snapshot?.toObject(User::class.java)
                _user.value = userObj
                
                viewModelScope.launch {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < 500) {
                        kotlinx.coroutines.delay(500 - elapsedTime)
                    }
                    _isLoading.value = false
                }
                
                userObj?.let {
                    val weekly = GoalUtils.generateWeeklyGoals(it.targetSteps, it)
                    val today = GoalUtils.getTodayGoal(weekly)
                    _todayTargetSteps.value = today.steps
                    _todayTargetBurnCalories.value = today.calories
                    _todayTargetCalories.value = if (it.targetCalories > 0) it.targetCalories else 2000
                    _todayTargetDistance.value = today.distance

                    val targetCals = if (it.targetCalories > 0) it.targetCalories else today.calories
                    val macros = GoalUtils.calculateMacroTargets(targetCals, it.goalType)
                    
                    _targetProtein.value = if (it.targetProtein > 0) it.targetProtein else macros.protein
                    _targetCarbs.value = if (it.targetCarbs > 0) it.targetCarbs else macros.carbs
                    _targetFat.value = if (it.targetFat > 0) it.targetFat else macros.fat

                    // Reschedule reminders if needed
                    it.reminders.forEach { reminder -> reminderManager?.scheduleReminder(reminder) }
                    it.wellnessReminders.forEach { reminder -> reminderManager?.scheduleReminder(reminder) }
                }
            }
        }
    }

    private fun fetchDailyData() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            db.collection("users").document(uid)
                .collection("daily_data").document(today)
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.toObject(DailyData::class.java)?.let {
                        _dailyData.value = it
                    }
                }
        }
    }

    fun startScan() {
        repository.startScan()
    }

    fun stopScan() {
        repository.stopScan()
    }

    fun connectDevice(address: String) {
        repository.connect(address)
    }

    fun disconnectDevice() {
        repository.disconnect()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshData()
            kotlinx.coroutines.delay(1000)
            _isRefreshing.value = false
        }
    }

    fun updateUser(updatedUser: User) {
        if (!checkNetwork()) {
            android.util.Log.e("DashboardVM", "No internet connection to update user")
            return
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            _isLoading.value = true
            try {
                db.collection("users").document(uid).set(updatedUser).await()
                android.util.Log.d("DashboardVM", "User profile updated successfully in Firestore")
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Error updating user profile", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateIdealSchedule(user: User? = null) {
        val userToUse = user ?: _user.value ?: return
        viewModelScope.launch {
            val idealReminders = IdealTimeUtils.generateIdealSchedule(userToUse)
            val updatedUser = userToUse.copy(wellnessReminders = idealReminders)
            updateUser(updatedUser)
        }
    }
}
