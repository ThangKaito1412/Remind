package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val description: String,
    val targetMuscle: String, // E.g., "Ngực", "Đùi", "Toàn thân", "Bụng"
    val sets: Int,
    val repsOrDuration: String, // E.g., "12 reps" or "30 giây"
    val difficulty: String, // "Dễ", "Trung bình", "Khó"
    val restTimeSeconds: Int = 30
)
