package com.example.myfitnessapp.utils

import com.example.myfitnessapp.models.Reminder
import com.example.myfitnessapp.models.User
import java.util.*

object IdealTimeUtils {

    fun generateIdealSchedule(user: User): List<Reminder> {
        val reminders = mutableListOf<Reminder>()
        
        val wakeUp = parseTime(user.wakeUpTime)
        val sleep = parseTime(user.sleepTime)
        val workStart = parseTime(user.workStartTime)
        val workEnd = parseTime(user.workEndTime)

        // 1. Water Reminders (Every 60-90 mins or more for physical work)
        val waterInterval = when (user.workStyle) {
            "Heavy Physical Work" -> 60
            else -> 90
        }
        addPeriodicReminders(reminders, "Water", "Stay hydrated! Drink some water. 💧", wakeUp, sleep, waterInterval)

        // 2. Standing & Walking (Depend on work style)
        when (user.workStyle) {
            "Mostly Sitting" -> {
                addPeriodicReminders(reminders, "Stand Up", "Time to stand up and stretch! 🧘", workStart, workEnd, 45)
                addPeriodicReminders(reminders, "Walk", "How about a 5-min walk? 🚶", workStart, workEnd, 120)
                
                // Increase eye exercises if screen time is high
                val eyeInterval = if (user.dailyScreenTime > 6) 60 else 120
                addPeriodicReminders(reminders, "Eye Exercise", "Rest your eyes, look away! 👀", workStart, workEnd, eyeInterval)
                
                addPeriodicReminders(reminders, "Stretch", "Neck and shoulder stretch time! 💆‍♂️", workStart, workEnd, 150)
            }
            "Mostly Standing" -> {
                addPeriodicReminders(reminders, "Sit Break", "Give your feet a rest! 🪑", workStart, workEnd, 90)
                addPeriodicReminders(reminders, "Leg Stretch", "Stretch those tired legs! 🦵", workStart, workEnd, 120)
                addPeriodicReminders(reminders, "Foot Relaxation", "Rotate your ankles! 🦶", workStart, workEnd, 180)
            }
            "Mixed Activity" -> {
                addPeriodicReminders(reminders, "Stretch", "Quick body stretch! 🤸", workStart, workEnd, 120)
                addPeriodicReminders(reminders, "Deep Breathing", "Take 3 deep breaths. 🌬️", workStart, workEnd, 180)
            }
            "Heavy Physical Work" -> {
                addPeriodicReminders(reminders, "Rest", "Time for a quick recovery. ☕", workStart, workEnd, 120)
                addPeriodicReminders(reminders, "Nutrition", "Fuel up with a healthy snack! 🍎", workStart, workEnd, 180)
            }
            "Driving" -> {
                // Reminders at safe break times
                addPeriodicReminders(reminders, "Safe Break", "Stop, stretch, and hydrate! ⛽", workStart, workEnd, 150)
                addPeriodicReminders(reminders, "Eye Relaxation", "Focus on the horizon for a bit. 🛣️", workStart, workEnd, 180)
            }
            "Home & Care" -> {
                addPeriodicReminders(reminders, "Posture", "Check your back and posture! ✨", wakeUp, sleep, 120)
                addPeriodicReminders(reminders, "Relaxation", "You're doing great, take a breath! 🌸", wakeUp, sleep, 180)
                addPeriodicReminders(reminders, "Stretching", "A quick stretch to stay limber! 🧘‍♀️", wakeUp, sleep, 150)
            }
        }

        // 3. Fixed Reminders (Excluding Lunch/Dinner as they are handled in Meal Reminders)
        
        // Exercise Habit
        if (user.exerciseHabit != "None") {
            addFixedReminder(reminders, "Exercise", "Time for your workout!", "18:30", wakeUp, sleep)
        }

        // Sleep Preparation
        val sleepPrepTime = Calendar.getInstance().apply {
            time = sleep
            add(Calendar.MINUTE, -30)
        }.time
        reminders.add(Reminder(
            id = UUID.randomUUID().toString(),
            type = "Sleep Preparation",
            message = "Time to wind down for sleep. 🌙",
            time = formatTime(sleepPrepTime),
            isEnabled = true
        ))

        return reminders.sortedBy { it.time }
    }

    private fun addPeriodicReminders(
        reminders: MutableList<Reminder>,
        type: String,
        message: String,
        start: Date,
        end: Date,
        intervalMinutes: Int
    ) {
        val calendar = Calendar.getInstance()
        calendar.time = start
        calendar.add(Calendar.MINUTE, intervalMinutes)

        while (calendar.time.before(end)) {
            val timeStr = formatTime(calendar.time)
            // Check if there's already a similar reminder within 45 mins
            if (!hasNearbyReminder(reminders, timeStr, 45)) {
                reminders.add(Reminder(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    message = message,
                    time = timeStr,
                    isEnabled = true
                ))
            }
            calendar.add(Calendar.MINUTE, intervalMinutes)
        }
    }

    private fun addFixedReminder(
        reminders: MutableList<Reminder>,
        type: String,
        message: String,
        timeStr: String,
        wakeUp: Date,
        sleep: Date
    ) {
        val time = parseTime(timeStr)
        if (time.after(wakeUp) && time.before(sleep)) {
            reminders.add(Reminder(
                id = UUID.randomUUID().toString(),
                type = type,
                message = message,
                time = timeStr,
                isEnabled = true
            ))
        }
    }

    private fun hasNearbyReminder(reminders: List<Reminder>, timeStr: String, thresholdMins: Int): Boolean {
        val time = parseTime(timeStr).time
        return reminders.any { 
            Math.abs(parseTime(it.time).time - time) < thresholdMins * 60 * 1000
        }
    }

    private fun parseTime(timeStr: String): Date {
        val parts = timeStr.split(":")
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts[1].toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun formatTime(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return "%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }
}
