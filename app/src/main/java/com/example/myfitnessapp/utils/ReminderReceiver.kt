package com.example.myfitnessapp.utils

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myfitnessapp.MainActivity
import com.example.myfitnessapp.R
import com.example.myfitnessapp.models.Reminder
import com.example.myfitnessapp.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("ReminderReceiver", "onReceive called with action: $action")
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                rescheduleAllReminders(context)
            }
            "com.example.myfitnessapp.ACTION_REMINDER" -> {
                // Check if user is logged in
                if (FirebaseAuth.getInstance().currentUser == null) {
                    Log.d("ReminderReceiver", "User not logged in, ignoring reminder")
                    return
                }

                val title = intent.getStringExtra("TITLE") ?: "Health Reminder"
                val message = intent.getStringExtra("MESSAGE") ?: "It's time for your health activity!"
                val reminderId = intent.getStringExtra("REMINDER_ID") ?: title
                
                val notificationId = reminderId.hashCode()
                showAlarmNotification(context, title, message, notificationId, true)

                // broadcast to stop the ringing after 30 seconds
                scheduleStopAction(context, notificationId, title, message)

                // Reschedule for next day to make it work daily
                val type = intent.getStringExtra("REMINDER_TYPE")
                val time = intent.getStringExtra("REMINDER_TIME")
                val originalMsg = intent.getStringExtra("REMINDER_MSG") ?: ""
                
                if (type != null && time != null) {
                    val nextReminder = Reminder(
                        id = reminderId,
                        type = type,
                        message = originalMsg,
                        time = time,
                        isEnabled = true
                    )
                    ReminderManager(context).scheduleReminder(nextReminder)
                    Log.d("ReminderReceiver", "Rescheduled reminder $reminderId for tomorrow")
                }
            }
            "com.example.myfitnessapp.ACTION_STOP_ALARM" -> {
                val title = intent.getStringExtra("TITLE") ?: ""
                val message = intent.getStringExtra("MESSAGE") ?: ""
                val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
                val isFromUser = intent.getBooleanExtra("IS_FROM_USER", false)
                
                if (notificationId != -1) {
                    // Always cancel the pending stop timer first
                    cancelStopTimer(context, notificationId)
                    
                    if (isFromUser) {
                        // User clicked Dismiss - remove the notification entirely
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationId)
                        Log.d("ReminderReceiver", "Notification $notificationId dismissed by user")
                    } else {
                        // Timer expired - just silence the alarm sound but keep the notification
                        stopAlarmRing(context, title, message, notificationId)
                    }
                }
            }
            "com.example.myfitnessapp.ACTION_CANCEL_STOP_ALARM" -> {
                val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
                if (notificationId != -1) {
                    cancelStopTimer(context, notificationId)
                }
            }
        }
    }

    private fun cancelStopTimer(context: Context, notificationId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val stopIntent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = "com.example.myfitnessapp.ACTION_STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            stopIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (stopPendingIntent != null) {
            alarmManager.cancel(stopPendingIntent)
            stopPendingIntent.cancel()
            Log.d("ReminderReceiver", "Cancelled stop timer for notification $notificationId")
        }
    }

    private fun rescheduleAllReminders(context: Context) {
        val pendingResult = goAsync()
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("users").document(userId).get().await()
                val user = doc.toObject(User::class.java) ?: return@launch
                
                val reminderManager = ReminderManager(context)
                
                // Reschedule all standard reminders
                user.reminders.forEach { reminder ->
                    if (reminder.isEnabled) {
                        reminderManager.scheduleReminder(reminder)
                    }
                }

                // Reschedule all wellness reminders
                user.wellnessReminders.forEach { reminder ->
                    if (reminder.isEnabled) {
                        reminderManager.scheduleReminder(reminder)
                    }
                }
                
                Log.d("ReminderReceiver", "Successfully rescheduled reminders after boot")
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Error rescheduling reminders after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleStopAction(context: Context, notificationId: Int, title: String, message: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val stopIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.myfitnessapp.ACTION_STOP_ALARM"
            putExtra("NOTIFICATION_ID", notificationId)
            putExtra("TITLE", title)
            putExtra("MESSAGE", message)
        }
        
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000, // Move further away to avoid collisions
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerAt = System.currentTimeMillis() + 30000 // 30 seconds
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, stopPendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, stopPendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, stopPendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e("ReminderReceiver", "SecurityException scheduling stop action", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, stopPendingIntent)
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Error scheduling stop action", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, stopPendingIntent)
        }
    }

    private fun stopAlarmRing(context: Context, title: String, message: String, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val isStillActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.activeNotifications.any { it.id == notificationId }
        } else {
            true
        }

        if (isStillActive) {
            Log.d("ReminderReceiver", "Silencing alarm sound for notification: $notificationId")
            // To reliably stop the insistent sound, we must cancel the old notification
            notificationManager.cancel(notificationId)
            // Re-post the notification without insistence and sound to update it in place
            showAlarmNotification(context, title, message, notificationId, false)
        }
    }

    private fun showAlarmNotification(context: Context, title: String, message: String, notificationId: Int, isInsistent: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "health_alarm_channel_v3"

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Health Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent reminders for meals and wellness"
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            notificationId, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.healthpilot_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)

        // Add delete intent to cancel stop action if user dismisses notification
        val deleteIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.myfitnessapp.ACTION_CANCEL_STOP_ALARM"
            putExtra("NOTIFICATION_ID", notificationId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2000,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationBuilder.setDeleteIntent(deletePendingIntent)

        if (isInsistent) {
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX)
            notificationBuilder.setFullScreenIntent(pendingIntent, true)
            notificationBuilder.setSound(alarmSound)
            notificationBuilder.setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            notificationBuilder.setOnlyAlertOnce(false)
            
            // Add action button to stop the alarm sound immediately
            val stopActionIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.myfitnessapp.ACTION_STOP_ALARM"
                putExtra("NOTIFICATION_ID", notificationId)
                putExtra("TITLE", title)
                putExtra("MESSAGE", message)
                putExtra("IS_FROM_USER", true)
            }
            val stopActionPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3000,
                stopActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(0, "Dismiss", stopActionPendingIntent)
        } else {
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            notificationBuilder.setFullScreenIntent(null, false)
            notificationBuilder.setSound(null)
            notificationBuilder.setVibrate(null)
            notificationBuilder.setOnlyAlertOnce(true)
        }

        val notification = notificationBuilder.build()
        if (isInsistent) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        } else {
            notification.flags = notification.flags and Notification.FLAG_INSISTENT.inv()
        }

        notificationManager.notify(notificationId, notification)
    }
}
