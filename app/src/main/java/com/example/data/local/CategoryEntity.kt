package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val iconName: String = "category",
    val colorHex: String = "#005FB0",
    val startDate: String = "",
    val reviewsCompleted: Int = 0,
    val folderName: String = "Mặc định",
    val reviewTime: String = "08:00",
    val interval1: Int = 1,
    val interval2: Int = 3,
    val interval3: Int = 7,
    val interval4: Int = 15,
    val interval5: Int = 30
)
