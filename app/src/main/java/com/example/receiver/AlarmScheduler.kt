package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.WorkoutScheduleEntity
import java.util.Calendar

object AlarmScheduler {

    fun scheduleWorkoutAlarm(context: Context, schedule: WorkoutScheduleEntity) {
        if (!schedule.isActive) {
            cancelWorkoutAlarm(context, schedule)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("CATEGORY_NAME", schedule.categoryName)
            putExtra("SCHEDULE_LABEL", schedule.label)
            putExtra("SCHEDULE_ID", schedule.id)
            putExtra("CATEGORY_ID", schedule.categoryId)
        }

        val requestCode = schedule.id.toInt()
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, schedule.hour)
            set(Calendar.MINUTE, schedule.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If the time already passed for today, set it for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

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
            Log.d("AlarmScheduler", "Successfully scheduled alarm for ${schedule.categoryName} at ${schedule.hour}:${schedule.minute} (timestamp: ${calendar.timeInMillis})")
        } catch (e: SecurityException) {
            // Fallback to non-exact alarm if exact alert permission is revoked
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.e("AlarmScheduler", "SecurityException scheduling exact alarm. Fallback to set()", e)
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error scheduling alarm", e)
        }
    }

    fun cancelWorkoutAlarm(context: Context, schedule: WorkoutScheduleEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        
        val requestCode = schedule.id.toInt()
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d("AlarmScheduler", "Cancelled alarm for schedule ID: ${schedule.id}")
    }

    fun scheduleSnoozeAlarm(context: Context, categoryId: Long, categoryName: String, label: String, minutesFromNow: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("CATEGORY_NAME", categoryName)
            putExtra("SCHEDULE_LABEL", "$label (Nhắc sau)")
            putExtra("CATEGORY_ID", categoryId)
            putExtra("SCHEDULE_ID", categoryId + 100000)
        }

        val requestCode = (categoryId + 100000).toInt()
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)

        val triggerTime = System.currentTimeMillis() + (minutesFromNow * 60L * 1000L)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Scheduled snooze alarm for $categoryName in $minutesFromNow minutes")
        } catch (e: Exception) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.e("AlarmScheduler", "Error scheduling snooze alarm, fallback used", e)
        }
    }
}
