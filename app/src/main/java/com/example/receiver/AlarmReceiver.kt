package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.data.local.WorkoutDatabase
import com.example.data.local.WorkoutLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        // Handle Boot Completed / Package Replaced / Quickboot
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("AlarmReceiver", "Device rebooted or app updated. Rescheduling all active alarms.")
            val database = WorkoutDatabase.getDatabase(context)
            val dao = database.workoutDao()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val allCategories = dao.getAllCategoriesList().associateBy { it.id }
                    val activeSchedules = dao.getAllSchedulesList().filter { it.isActive }
                    for (schedule in activeSchedules) {
                        val category = allCategories[schedule.categoryId]
                        val isCompleted = if (category != null) {
                            when (category.intervalType) {
                                "specific_date" -> category.reviewsCompleted >= 1
                                "every_day", "every_n_days", "every_n_hours" -> false
                                else -> category.reviewsCompleted >= 5
                            }
                        } else false
                        
                        if (!isCompleted) {
                            AlarmScheduler.scheduleWorkoutAlarm(context, schedule)
                        } else {
                            Log.d("AlarmReceiver", "Skipping boot alarm for completed category ${schedule.categoryName}")
                        }
                    }
                    Log.d("AlarmReceiver", "Rescheduled active alarms successfully after boot.")
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Error rescheduling alarms on boot", e)
                }
            }
            return
        }

        val categoryId = intent.getLongExtra("CATEGORY_ID", -1L)
        val categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "Luyện Tập Hằng Ngày"
        val scheduleLabel = intent.getStringExtra("SCHEDULE_LABEL") ?: "Lịch Luyện Tập"
        
        Log.d("AlarmReceiver", "Alarm received for action=$action, $categoryName - $scheduleLabel (ID: $categoryId)")
        
        if (action == "com.example.ACTION_SNOOZE_30") {
            // Dismiss notification immediately
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(categoryName.hashCode() + scheduleLabel.hashCode())
            
            if (categoryId != -1L) {
                AlarmScheduler.scheduleSnoozeAlarm(
                    context = context,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    label = scheduleLabel,
                    minutesFromNow = 30
                )
                Toast.makeText(context, "Đã hẹn nhắc lại bài tập '$categoryName' sau 30 phút! ⏰", Toast.LENGTH_LONG).show()
            }
            return
        }

        if (action == "com.example.ACTION_COMPLETE") {
            if (categoryId != -1L) {
                val database = WorkoutDatabase.getDatabase(context)
                val dao = database.workoutDao()
                CoroutineScope(Dispatchers.IO).launch {
                    val category = dao.getCategoryById(categoryId)
                    if (category != null) {
                        val nextVal = when (category.intervalType) {
                            "specific_date" -> (category.reviewsCompleted + 1).coerceAtMost(1)
                            "every_day", "every_n_days", "every_n_hours" -> category.reviewsCompleted + 1
                            else -> (category.reviewsCompleted + 1).coerceAtMost(5)
                        }
                        dao.updateCategory(category.copy(reviewsCompleted = nextVal))
                        
                        val isCompleted = when (category.intervalType) {
                            "specific_date" -> nextVal >= 1
                            "every_day", "every_n_days", "every_n_hours" -> false
                            else -> nextVal >= 5
                        }
                        if (isCompleted) {
                            val categorySchedules = dao.getAllSchedulesList().filter { it.categoryId == category.id }
                            for (sched in categorySchedules) {
                                AlarmScheduler.cancelWorkoutAlarm(context, sched)
                                dao.updateSchedule(sched.copy(isActive = false))
                            }
                        }
                        
                        // Add a workout log
                        dao.insertLog(WorkoutLogEntity(
                            exerciseId = category.id,
                            exerciseName = category.name,
                            categoryName = "Ôn Tập Lặp Lại",
                            completedTimestamp = System.currentTimeMillis(),
                            note = "Hoàn thành nhanh qua thông báo",
                            rating = 5,
                            durationSeconds = 600
                        ))
                        
                        // Add 1 lucky spin without auto-switching to lucky wheel
                        val sharedPrefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
                        val currentSpins = sharedPrefs.getInt("available_spins", 0)
                        sharedPrefs.edit()
                            .putInt("available_spins", currentSpins + 1)
                            .apply()
                        
                        // Dismiss notification
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        notificationManager.cancel(categoryName.hashCode() + scheduleLabel.hashCode())
                        
                        withContext(Dispatchers.Main) {
                            val msg = if (isCompleted) {
                                "Chúc mừng! Bạn đã hoàn thành toàn bộ 5 lần ôn tập cho '${category.name}'! 🎉"
                            } else {
                                "Đã ghi nhận hoàn thành '${category.name}'! Nhận 1 lượt quay! 🎁"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            return
        }
        
        val scheduleId = intent.getLongExtra("SCHEDULE_ID", -1L)
        val database = WorkoutDatabase.getDatabase(context)
        val dao = database.workoutDao()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. If categoryId is present, verify that the category exists and is not completed
                if (categoryId != -1L) {
                    val category = dao.getCategoryById(categoryId)
                    if (category == null) {
                        Log.d("AlarmReceiver", "Category ID $categoryId was deleted. Ignoring alarm and skipping notification.")
                        return@launch
                    }
                    val isCompleted = when (category.intervalType) {
                        "specific_date" -> category.reviewsCompleted >= 1
                        "every_day", "every_n_days", "every_n_hours" -> false
                        else -> category.reviewsCompleted >= 5
                    }
                    if (isCompleted) {
                        Log.d("AlarmReceiver", "Category '${category.name}' has completed 5/5 reviews. Skipping notification and disabling alarm.")
                        if (scheduleId != -1L && scheduleId < 100000) {
                            val schedule = dao.getScheduleById(scheduleId)
                            if (schedule != null) {
                                AlarmScheduler.cancelWorkoutAlarm(context, schedule)
                                dao.updateSchedule(schedule.copy(isActive = false))
                            }
                        }
                        return@launch
                    }
                }
                
                // 2. If scheduleId is present and is not a snooze alarm (snooze schedule IDs are categoryId + 100000)
                if (scheduleId != -1L && scheduleId < 100000) {
                    val schedule = dao.getScheduleById(scheduleId)
                    if (schedule == null || !schedule.isActive) {
                        Log.d("AlarmReceiver", "Schedule ID $scheduleId is null or inactive. Skipping alarm/notification.")
                        return@launch
                    } else {
                        // Reschedule for next time
                        AlarmScheduler.scheduleWorkoutAlarm(context, schedule)
                        Log.d("AlarmReceiver", "Rescheduled active alarm for ${schedule.categoryName} (ID: ${schedule.id}).")
                    }
                }
                
                // 3. Show notification
                withContext(Dispatchers.Main) {
                    try {
                        val notificationHelper = NotificationHelper(context)
                        notificationHelper.showWorkoutReminder(categoryName, scheduleLabel, categoryId)
                    } catch (e: Exception) {
                        Log.e("AlarmReceiver", "Failed to trigger notification", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error processing alarm", e)
            }
        }
    }
}
