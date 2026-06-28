package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    // --- Categories (Folders) ---
    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesList(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    // --- Exercises ---
    @Query("SELECT * FROM exercises ORDER BY id ASC")
    fun getAllExercisesFlow(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercisesList(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE categoryId = :categoryId ORDER BY id ASC")
    fun getExercisesByCategoryIdFlow(categoryId: Long): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE categoryId = :categoryId ORDER BY id ASC")
    suspend fun getExercisesByCategoryIdList(categoryId: Long): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: Long): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    // --- Workout Schedules ---
    @Query("SELECT * FROM workout_schedules ORDER BY hour ASC, minute ASC")
    fun getAllSchedulesFlow(): Flow<List<WorkoutScheduleEntity>>

    @Query("SELECT * FROM workout_schedules ORDER BY hour ASC, minute ASC")
    suspend fun getAllSchedulesList(): List<WorkoutScheduleEntity>

    @Query("SELECT * FROM workout_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): WorkoutScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: WorkoutScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: WorkoutScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: WorkoutScheduleEntity)

    // --- Workout Logs (History) ---
    @Query("SELECT * FROM workout_logs ORDER BY completedTimestamp DESC")
    fun getAllLogsFlow(): Flow<List<WorkoutLogEntity>>

    @Query("SELECT * FROM workout_logs ORDER BY completedTimestamp DESC")
    suspend fun getAllLogsList(): List<WorkoutLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: WorkoutLogEntity): Long

    @Delete
    suspend fun deleteLog(log: WorkoutLogEntity)

    @Query("DELETE FROM exercises WHERE categoryId = :categoryId")
    suspend fun deleteExercisesByCategoryId(categoryId: Long)

    @Query("DELETE FROM workout_schedules WHERE categoryId = :categoryId")
    suspend fun deleteSchedulesByCategoryId(categoryId: Long)

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    @Query("DELETE FROM exercises")
    suspend fun clearAllExercises()

    @Query("DELETE FROM workout_schedules")
    suspend fun clearAllSchedules()

    @Query("DELETE FROM workout_logs")
    suspend fun clearAllLogs()
}
