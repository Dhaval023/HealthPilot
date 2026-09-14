package com.example.myfitnessapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.myfitnessapp.models.Reminder
import java.util.*

class ReminderManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(reminder: Reminder, alarmTune: String = "default") {
        if (!reminder.isEnabled) {
            cancelReminder(reminder)
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.myfitnessapp.ACTION_REMINDER"
            putExtra("TITLE", "${reminder.type} Reminder")
            putExtra("MESSAGE", if (reminder.message.isNotEmpty()) reminder.message else "Time for your ${reminder.type.lowercase()}!")
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TYPE", reminder.type)
            putExtra("REMINDER_TIME", reminder.time)
            putExtra("REMINDER_MSG", reminder.message)
            putExtra("ALARM_TUNE", alarmTune)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeParts = reminder.time.split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        Log.d("ReminderManager", "Scheduling reminder ${reminder.id} for ${calendar.time}")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("ReminderManager", "SecurityException scheduling alarm", e)
            // Fallback for Android 12+ if SCHEDULE_EXACT_ALARM is denied
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelReminder(reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.myfitnessapp.ACTION_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("ReminderManager", "Cancelled reminder ${reminder.id}")
    }
}
