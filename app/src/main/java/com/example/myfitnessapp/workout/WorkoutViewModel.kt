package com.example.myfitnessapp.workout

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.ble.BleManager
import com.example.myfitnessapp.data.workout.WorkoutRecord
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val repository = (application as HealthPilot).repository

    val connectionState = repository.connectionState
    val connectedDeviceName = repository.connectedDeviceName
    val foundDevices = repository.foundDevices

    private val _timerText = MutableLiveData("00:00:00")
    val timerText: LiveData<String> = _timerText

    private val _secondsElapsed = MutableLiveData(0L)
    val secondsElapsed: LiveData<Long> = _secondsElapsed

    private val _steps = MutableLiveData(0)
    val steps: LiveData<Int> = _steps

    private val _distance = MutableLiveData(0.0)
    val distance: LiveData<Double> = _distance

    private val _calories = MutableLiveData(0.0)
    val calories: LiveData<Double> = _calories

    private val _heartRate = MutableLiveData<Int?>(null)
    val heartRate: LiveData<Int?> = _heartRate

    private val _isTracking = MutableLiveData(false)
    val isTracking: LiveData<Boolean> = _isTracking

    var workoutType: String = ""
    private var startTimeMillis = 0L

    private var trackingService: WorkoutTrackingService? = null
    private var isBound = false
    private var updateJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WorkoutTrackingService.LocalBinder
            trackingService = binder.getService()
            isBound = true
            startUpdatingMetrics()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            isBound = false
            updateJob?.cancel()
        }
    }

    init {
        val intent = Intent(application, WorkoutTrackingService::class.java)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
        updateJob?.cancel()
    }

    fun connectToDevice(address: String) {
        repository.connect(address)
    }

    fun startScan() = repository.startScan()
    fun stopScan() = repository.stopScan()

    private fun startUpdatingMetrics() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            while (true) {
                trackingService?.let { service ->
                    Log.d("WorkoutViewModel", "Updating metrics from service: steps=${service.steps}, time=${service.secondsElapsed}")
                    _isTracking.postValue(service.isTracking)
                    _steps.postValue(service.steps)
                    _timerText.postValue(formatTime(service.secondsElapsed))
                    _secondsElapsed.postValue(service.secondsElapsed)
                    updateMetrics(service.steps)
                    updateHeartRateSimulated()
                }
                delay(1000)
            }
        }
    }

    fun startWorkout(type: String) {
        workoutType = type
        startTimeMillis = System.currentTimeMillis()
        
        val intent = Intent(getApplication(), WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_START
            putExtra(WorkoutTrackingService.EXTRA_WORKOUT_TYPE, type)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun resumeWorkout() {
        val intent = Intent(getApplication(), WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_RESUME
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun pauseWorkout() {
        val intent = Intent(getApplication(), WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_PAUSE
        }
        getApplication<Application>().startForegroundService(intent)
    }

    private fun updateMetrics(currentSteps: Int) {
        val kmPerStep = 0.0008
        _distance.postValue(currentSteps * kmPerStep)
        
        val caloriesPerStep = 0.04
        
        if (workoutType.lowercase() == "yoga") {
            // Yoga burns ~3-6 calories per minute depending on intensity
            val seconds = trackingService?.secondsElapsed ?: 0L
            val minutes = seconds / 60.0
            _calories.postValue(minutes * 4.5) // Average yoga burn
        } else if (workoutType.lowercase() == "swimming") {
            val seconds = trackingService?.secondsElapsed ?: 0L
            _calories.postValue((seconds / 60.0) * 8.0) // ~8 kcal/min for swimming
        } else if (workoutType.lowercase() == "boxing") {
            val seconds = trackingService?.secondsElapsed ?: 0L
            _calories.postValue((seconds / 60.0) * 10.0) // ~10 kcal/min for boxing
        } else {
            // For step-based workouts (walking, running)
            _calories.postValue(currentSteps * caloriesPerStep)
        }
    }

    private fun updateHeartRateSimulated() {
        if (_isTracking.value != true) return
        val baseHR = if (workoutType.lowercase() == "running") 130 else 80
        val randomVariation = (Math.random() * 10).toInt()
        _heartRate.postValue(baseHR + randomVariation)
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    fun finishWorkout(save: Boolean, onFinished: (String?) -> Unit) {
        val intent = Intent(getApplication(), WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)

        if (!save) {
            onFinished(null)
            return
        }
        
        val endTimeMillis = System.currentTimeMillis()
        val userId = auth.currentUser?.uid ?: return
        val finalSeconds = trackingService?.secondsElapsed ?: 0L
        val finalSteps = trackingService?.steps ?: 0
        
        val record = WorkoutRecord(
            userId = userId,
            workoutType = workoutType,
            date = getStartOfDay(startTimeMillis),
            startTime = startTimeMillis,
            endTime = endTimeMillis,
            duration = finalSeconds * 1000,
            totalSteps = finalSteps,
            totalDistance = finalSteps * 0.0008,
            caloriesBurned = finalSteps * 0.04,
            averageHeartRate = _heartRate.value
        )
        
        viewModelScope.launch {
            try {
                Log.d("WorkoutViewModel", "Attempting to save workout: $workoutType for user: $userId")
                val workoutTypeRef = db.collection("users").document(userId)
                    .collection("workouts").document(workoutType)
                
                workoutTypeRef.set(mapOf("lastUpdated" to System.currentTimeMillis()), SetOptions.merge()).await()
                
                val docRef = workoutTypeRef.collection("dailyRecords").add(record).await()
                onFinished(docRef.id)
            } catch (e: Exception) {
                Log.e("WorkoutViewModel", "Error saving workout: ${e.message}", e)
                onFinished(null)
            }
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
