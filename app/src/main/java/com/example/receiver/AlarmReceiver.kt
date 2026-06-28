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
        val categoryId = intent.getLongExtra("CATEGORY_ID", -1L)
        val categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "Luyện Tập Hằng Ngày"
        val scheduleLabel = intent.getStringExtra("SCHEDULE_LABEL") ?: "Lịch Luyện Tập"
        
        Log.d("AlarmReceiver", "Alarm received for action=$action, $categoryName - $scheduleLabel (ID: $categoryId)")
        
        if (action == "com.example.ACTION_COMPLETE") {
            if (categoryId != -1L) {
                val database = WorkoutDatabase.getDatabase(context)
                val dao = database.workoutDao()
                CoroutineScope(Dispatchers.IO).launch {
                    val category = dao.getCategoryById(categoryId)
                    if (category != null) {
                        val nextVal = (category.reviewsCompleted + 1).coerceAtMost(5)
                        dao.updateCategory(category.copy(reviewsCompleted = nextVal))
                        
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
                        
                        // Add 1 lucky spin!
                        val sharedPrefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
                        val currentSpins = sharedPrefs.getInt("available_spins", 0)
                        sharedPrefs.edit()
                            .putInt("available_spins", currentSpins + 1)
                            .putBoolean("trigger_lucky_wheel_on_open", true)
                            .apply()
                        
                        // Dismiss notification
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        notificationManager.cancel(categoryName.hashCode() + scheduleLabel.hashCode())
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Đã ghi nhận hoàn thành '${category.name}'! Mở ứng dụng để quay số nhận thưởng! 🎁", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            return
        }
        
        try {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showWorkoutReminder(categoryName, scheduleLabel, categoryId)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to trigger notification", e)
        }
    }
}
