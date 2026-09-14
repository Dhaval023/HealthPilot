package com.example.myfitnessapp.workout

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.MainActivity
import com.example.myfitnessapp.R
import com.example.myfitnessapp.ble.BleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class WorkoutTrackingService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var bleManager: BleManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()
    
    // Tracking Data
    var secondsElapsed = 0L
    var steps = 0
    var phoneSteps = 0
    var watchSteps = 0
    var initialPhoneSteps = -1
    var initialWatchSteps = -1
    var isTracking = false
    var workoutType = ""
    
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTracking) {
                secondsElapsed++
                updateNotification()
                handler.postDelayed(this, 1000)
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): WorkoutTrackingService = this@WorkoutTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        bleManager = (application as HealthPilot).bleManager
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorkoutTracking::WakeLock")

        observeWatchSteps()
    }

    private fun observeWatchSteps() {
        serviceScope.launch {
            bleManager.healthData.collectLatest { data ->
                if (!isTracking) return@collectLatest
                
                val currentTotalWatchSteps = data.steps
                Log.d("WorkoutService", "Watch Data: Total=$currentTotalWatchSteps, Initial=$initialWatchSteps, State=${bleManager.connectionState.value}")

                if (bleManager.connectionState.value == BleManager.ConnectionState.CONNECTED && currentTotalWatchSteps >= 0) {
                    if (initialWatchSteps == -1) {
                        initialWatchSteps = currentTotalWatchSteps
                        Log.d("WorkoutService", "Initial Watch Steps Baseline set: $initialWatchSteps")
                    }
                    
                    val relativeWatchSteps = currentTotalWatchSteps - initialWatchSteps
                    if (relativeWatchSteps >= 0) {
                        watchSteps = relativeWatchSteps
                        updateTotalSteps()
                    }
                }
            }
        }
    }

    private fun updateTotalSteps() {
        // Use the maximum from either source to ensure we don't miss steps
        steps = maxOf(phoneSteps, watchSteps)
        Log.d("WorkoutService", "Combined Steps: Total=$steps (Phone=$phoneSteps, Watch=$watchSteps)")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startTracking(intent.getStringExtra(EXTRA_WORKOUT_TYPE) ?: "Workout")
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking(type: String) {
        if (isTracking) return
        
        workoutType = type
        isTracking = true
        secondsElapsed = 0L
        initialPhoneSteps = -1
        initialWatchSteps = -1
        steps = 0
        phoneSteps = 0
        watchSteps = 0
        
        wakeLock?.acquire()
        registerSensor()
        handler.post(timerRunnable)
        startForegroundService()
    }

    private fun resumeTracking() {
        if (isTracking) return
        isTracking = true
        wakeLock?.acquire()
        registerSensor()
        handler.post(timerRunnable)
        updateNotification()
    }

    private fun pauseTracking() {
        isTracking = false
        handler.removeCallbacks(timerRunnable)
        unregisterSensor()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        updateNotification()
    }

    private fun stopTracking() {
        isTracking = false
        handler.removeCallbacks(timerRunnable)
        unregisterSensor()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerSensor() {
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepDetectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    private fun unregisterSensor() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTracking) return

        when (event?.sensor?.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toInt()
                if (initialPhoneSteps == -1) {
                    initialPhoneSteps = totalSteps
                    Log.d("WorkoutService", "Initial Phone Steps Baseline set: $initialPhoneSteps")
                }
                val currentPhoneSteps = totalSteps - initialPhoneSteps
                // Only update if it's greater to avoid jumps
                if (currentPhoneSteps > phoneSteps) {
                    phoneSteps = currentPhoneSteps
                    updateTotalSteps()
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                // Step detector triggers for every step (value is 1.0)
                if (event.values[0] > 0) {
                    // If we haven't received a counter update yet, we can increment manually
                    // but we must be careful not to double count once the counter catches up.
                    // For now, we'll just log it to verify movement detection.
                    Log.d("WorkoutService", "Step Detector Triggered")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startForegroundService() {
        val channelId = "workout_tracking_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Workout Tracking", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val timeText = String.format("%02d:%02d:%02d", secondsElapsed / 3600, (secondsElapsed % 3600) / 60, secondsElapsed % 60)
        
        return NotificationCompat.Builder(this, "workout_tracking_channel")
            .setContentTitle("Tracking $workoutType")
            .setContentText("Time: $timeText | Steps: $steps")
            .setSmallIcon(R.drawable.ic_walk)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_WORKOUT_TYPE = "EXTRA_WORKOUT_TYPE"
    }
}
