package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_schedules")
data class WorkoutScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long, // Folder to link to
    val categoryName: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String, // Comma separated, e.g., "Thứ Hai, Thứ Tư, Thứ Sáu" or "2,4,6"
    val isActive: Boolean = true,
    val label: String
)
