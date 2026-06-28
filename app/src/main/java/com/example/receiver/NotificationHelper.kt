package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "com.aistudio.fitminder.alerts"
        const val CHANNEL_NAME = "Lịch nhắc nhở Luyện Tập"
        const val CHANNEL_DESC = "Nhắc nhở giờ tập cho từng chủ đề / thư mục luyện tập"
        const val NOTIFICATION_ID = 2605
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showWorkoutReminder(categoryName: String, label: String, categoryId: Long = -1) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

        // Notification custom notes from SharedPreferences
        val sharedPrefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
        val customNote = if (categoryId != -1L) sharedPrefs.getString("notif_note_$categoryId", null) else null

        val notificationTitle = "🔔 $categoryName đã Đến Giờ Luyện Tập"
        val notificationContent = if (!customNote.isNullOrBlank()) {
            customNote
        } else {
            "Lịch nhắc nhở ôn tập định kỳ cho bài tập '$categoryName' đang chờ bạn rèn luyện. Sẵn sàng thôi!"
        }

        // Action 1: Mark Completed
        val completeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_COMPLETE"
            putExtra("CATEGORY_ID", categoryId)
            putExtra("CATEGORY_NAME", categoryName)
            putExtra("SCHEDULE_LABEL", label)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            categoryId.toInt() + 10000,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Action 2: Go to Workout
        val goIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NAVIGATE_TO_TOPIC_ID", categoryId)
        }
        val goPendingIntent = PendingIntent.getActivity(
            context,
            categoryId.toInt() + 20000,
            goIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Action 3: Snooze Workout
        val snoozeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SNOOZE_CATEGORY_ID", categoryId)
            putExtra("SNOOZE_CATEGORY_NAME", categoryName)
            putExtra("SNOOZE_LABEL", label)
        }
        val snoozePendingIntent = PendingIntent.getActivity(
            context,
            categoryId.toInt() + 30000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Safe system fallback
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(goPendingIntent) // Clicking notification itself acts like "Go to practice"
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(android.R.drawable.ic_menu_save, "Đã hoàn thành", completePendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "Luyện tập", goPendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Nhắc sau", snoozePendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(categoryName.hashCode() + label.hashCode(), builder.build())
    }
}
