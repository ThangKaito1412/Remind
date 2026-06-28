package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_logs")
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String,
    val categoryName: String,
    val completedTimestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val rating: Int = 3, // Rating e.g., 1=Mệt, 2=Bình Thường, 3=Sung Sức
    val durationSeconds: Int = 0
)
