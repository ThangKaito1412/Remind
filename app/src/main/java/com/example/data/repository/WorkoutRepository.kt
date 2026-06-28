package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val context: Context
) {
    // --- SharedPreferences for Mock Google Account & Sync Status ---
    private val prefs: SharedPreferences = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)

    // Flow Data
    val allCategories: Flow<List<CategoryEntity>> = workoutDao.getAllCategoriesFlow()
    val allExercises: Flow<List<ExerciseEntity>> = workoutDao.getAllExercisesFlow()
    val allSchedules: Flow<List<WorkoutScheduleEntity>> = workoutDao.getAllSchedulesFlow()
    val allLogs: Flow<List<WorkoutLogEntity>> = workoutDao.getAllLogsFlow()

    fun getExercisesByCategory(categoryId: Long): Flow<List<ExerciseEntity>> {
        return workoutDao.getExercisesByCategoryIdFlow(categoryId)
    }

    // Prepopulate if DB is empty
    suspend fun checkAndPrepopulate() = withContext(Dispatchers.IO) {
        // Disabled prepopulation to keep the workspace completely clean of any mock or example categories.
        prefs.edit().putBoolean("has_prepopulated_v3", true).apply()
    }

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        workoutDao.clearAllCategories()
        workoutDao.clearAllExercises()
        workoutDao.clearAllSchedules()
        workoutDao.clearAllLogs()
    }

    // --- Category DB Operations ---
    suspend fun insertCategory(category: CategoryEntity): Long = withContext(Dispatchers.IO) {
        workoutDao.insertCategory(category)
    }
    suspend fun updateCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        workoutDao.updateCategory(category)
    }
    suspend fun deleteCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        workoutDao.deleteExercisesByCategoryId(category.id)
        workoutDao.deleteSchedulesByCategoryId(category.id)
        workoutDao.deleteCategory(category)
    }

    // --- Exercise DB Operations ---
    suspend fun insertExercise(exercise: ExerciseEntity): Long = withContext(Dispatchers.IO) {
        workoutDao.insertExercise(exercise)
    }
    suspend fun updateExercise(exercise: ExerciseEntity) = withContext(Dispatchers.IO) {
        workoutDao.updateExercise(exercise)
    }
    suspend fun deleteExercise(exercise: ExerciseEntity) = withContext(Dispatchers.IO) {
        workoutDao.deleteExercise(exercise)
    }
    suspend fun getExerciseById(id: Long): ExerciseEntity? = withContext(Dispatchers.IO) {
        workoutDao.getExerciseById(id)
    }

    // --- Schedule DB Operations ---
    suspend fun insertSchedule(schedule: WorkoutScheduleEntity): Long = withContext(Dispatchers.IO) {
        workoutDao.insertSchedule(schedule)
    }
    suspend fun updateSchedule(schedule: WorkoutScheduleEntity) = withContext(Dispatchers.IO) {
        workoutDao.updateSchedule(schedule)
    }
    suspend fun deleteSchedule(schedule: WorkoutScheduleEntity) = withContext(Dispatchers.IO) {
        workoutDao.deleteSchedule(schedule)
    }
    suspend fun clearAllSchedules() = withContext(Dispatchers.IO) {
        workoutDao.clearAllSchedules()
    }
    suspend fun getAllSchedulesList(): List<WorkoutScheduleEntity> = withContext(Dispatchers.IO) {
        workoutDao.getAllSchedulesList()
    }

    // --- Log DB Operations ---
    suspend fun insertLog(log: WorkoutLogEntity): Long = withContext(Dispatchers.IO) {
        workoutDao.insertLog(log)
    }
    suspend fun deleteLog(log: WorkoutLogEntity) = withContext(Dispatchers.IO) {
        workoutDao.deleteLog(log)
    }

    // --- Real Firebase Login and Cloud Sync Logic ---
    fun saveUserLoginInfo(name: String, email: String, avatarUrl: String?) {
        // No-op because Firebase Auth handles this natively
    }

    fun logoutUser() {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error signing out of Firebase Auth", e)
        }
    }

    fun getUserLoginState(): Pair<Boolean, Triple<String, String, String?>> {
        try {
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                val name = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "Người dùng"
                val email = firebaseUser.email ?: "vi_du@gmail.com"
                val avatar = firebaseUser.photoUrl?.toString()
                return Pair(true, Triple(name, email, avatar))
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error reading Firebase Auth state", e)
        }
        return Pair(false, Triple("N/A", "N/A", null))
    }

    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean("AUTO_SYNC", true)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("AUTO_SYNC", enabled).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong("LAST_SYNC_TIME", 0L)
    }

    suspend fun performCloudSync(): Long = withContext(Dispatchers.IO) {
        val loginState = getUserLoginState()
        val email = loginState.second.second
        if (email.isEmpty() || email == "N/A" || !loginState.first) {
            throw Exception("Vui lòng đăng nhập tài khoản trước khi đồng bộ!")
        }
        
        // Count all local data items to show in the sync summary
        val categoriesList = workoutDao.getAllCategoriesList()
        val schedulesList = workoutDao.getAllSchedulesList()
        val logsList = workoutDao.getAllLogsList()
        val exercisesList = workoutDao.getAllExercisesList()

        val rootMap = mutableMapOf<String, Any>()
        
        // Convert categories
        val categoriesData = categoriesList.map { cat ->
            mapOf(
                "id" to cat.id,
                "name" to cat.name,
                "description" to cat.description,
                "iconName" to cat.iconName,
                "colorHex" to cat.colorHex,
                "startDate" to cat.startDate,
                "reviewsCompleted" to cat.reviewsCompleted,
                "folderName" to cat.folderName,
                "reviewTime" to cat.reviewTime,
                "interval1" to cat.interval1,
                "interval2" to cat.interval2,
                "interval3" to cat.interval3,
                "interval4" to cat.interval4,
                "interval5" to cat.interval5
            )
        }
        rootMap["categories"] = categoriesData

        // Convert exercises
        val exercisesData = exercisesList.map { ex ->
            mapOf(
                "id" to ex.id,
                "categoryId" to ex.categoryId,
                "name" to ex.name,
                "description" to ex.description,
                "targetMuscle" to ex.targetMuscle,
                "sets" to ex.sets,
                "repsOrDuration" to ex.repsOrDuration,
                "difficulty" to ex.difficulty,
                "restTimeSeconds" to ex.restTimeSeconds
            )
        }
        rootMap["exercises"] = exercisesData

        // Convert schedules
        val schedulesData = schedulesList.map { sc ->
            mapOf(
                "id" to sc.id,
                "categoryId" to sc.categoryId,
                "categoryName" to sc.categoryName,
                "hour" to sc.hour,
                "minute" to sc.minute,
                "daysOfWeek" to sc.daysOfWeek,
                "isActive" to sc.isActive,
                "label" to sc.label
            )
        }
        rootMap["schedules"] = schedulesData

        // Convert logs
        val logsData = logsList.map { lg ->
            mapOf(
                "id" to lg.id,
                "exerciseId" to lg.exerciseId,
                "exerciseName" to lg.exerciseName,
                "categoryName" to lg.categoryName,
                "completedTimestamp" to lg.completedTimestamp,
                "note" to lg.note,
                "rating" to lg.rating,
                "durationSeconds" to lg.durationSeconds
            )
        }
        rootMap["logs"] = logsData

        val sanitizedEmail = email.replace(".", "_").replace("@", "_")
        
        val database = getFirebaseDatabase()
        val dbRef = database.getReference("users").child(sanitizedEmail)
        
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            dbRef.setValue(rootMap).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(Unit) {}
                } else {
                    continuation.resumeWith(Result.failure(task.exception ?: Exception("Lỗi đồng bộ dữ liệu tới Firebase")))
                }
            }
        }

        val now = System.currentTimeMillis()
        val totalCount = categoriesList.size + schedulesList.size + logsList.size + exercisesList.size
        prefs.edit().apply {
            putLong("LAST_SYNC_TIME", now)
            putInt("CLOUD_ITEMS_COUNT", totalCount)
            apply()
        }
        now
    }

    suspend fun restoreCloudSync(): Boolean = withContext(Dispatchers.IO) {
        val email = getUserLoginState().second.second
        if (email.isEmpty() || email == "N/A") return@withContext false
        
        // ALWAYS clean the local database first to ensure complete separation of accounts.
        // Even if the user doesn't have any cloud-synced data yet, they should see a completely clean screen on login.
        workoutDao.clearAllCategories()
        workoutDao.clearAllExercises()
        workoutDao.clearAllSchedules()
        workoutDao.clearAllLogs()
        
        val sanitizedEmail = email.replace(".", "_").replace("@", "_")
        val database = getFirebaseDatabase()
        val dbRef = database.getReference("users").child(sanitizedEmail)
        
        try {
            val snapshot = kotlinx.coroutines.suspendCancellableCoroutine<com.google.firebase.database.DataSnapshot?> { continuation ->
                dbRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        continuation.resume(snapshot) {}
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        continuation.resumeWith(Result.failure(error.toException()))
                    }
                })
            } ?: return@withContext false
            
            if (!snapshot.exists()) {
                // No backup exists on Firebase. This is a clean/new account, so starting with empty DB is correct.
                prefs.edit().apply {
                    putInt("CLOUD_ITEMS_COUNT", 0)
                    putLong("LAST_SYNC_TIME", 0L)
                    apply()
                }
                return@withContext false
            }
            
            // Repopulate categories
            if (snapshot.hasChild("categories")) {
                val catsSnap = snapshot.child("categories")
                catsSnap.children.forEach { catSnap ->
                    workoutDao.insertCategory(
                        CategoryEntity(
                            id = catSnap.child("id").getValue(Long::class.java) ?: 0L,
                            name = catSnap.child("name").getValue(String::class.java) ?: "",
                            description = catSnap.child("description").getValue(String::class.java) ?: "",
                            iconName = catSnap.child("iconName").getValue(String::class.java) ?: "category",
                            colorHex = catSnap.child("colorHex").getValue(String::class.java) ?: "#005FB0",
                            startDate = catSnap.child("startDate").getValue(String::class.java) ?: "",
                            reviewsCompleted = catSnap.child("reviewsCompleted").getValue(Int::class.java) ?: 0,
                            folderName = catSnap.child("folderName").getValue(String::class.java) ?: "Mặc định",
                            reviewTime = catSnap.child("reviewTime").getValue(String::class.java) ?: "08:00",
                            interval1 = catSnap.child("interval1").getValue(Int::class.java) ?: 1,
                            interval2 = catSnap.child("interval2").getValue(Int::class.java) ?: 3,
                            interval3 = catSnap.child("interval3").getValue(Int::class.java) ?: 7,
                            interval4 = catSnap.child("interval4").getValue(Int::class.java) ?: 15,
                            interval5 = catSnap.child("interval5").getValue(Int::class.java) ?: 30
                        )
                    )
                }
            }

            // Repopulate exercises
            if (snapshot.hasChild("exercises")) {
                val exercisesSnap = snapshot.child("exercises")
                exercisesSnap.children.forEach { exSnap ->
                    workoutDao.insertExercise(
                        ExerciseEntity(
                            id = exSnap.child("id").getValue(Long::class.java) ?: 0L,
                            categoryId = exSnap.child("categoryId").getValue(Long::class.java) ?: 0L,
                            name = exSnap.child("name").getValue(String::class.java) ?: "",
                            description = exSnap.child("description").getValue(String::class.java) ?: "",
                            targetMuscle = exSnap.child("targetMuscle").getValue(String::class.java) ?: "",
                            sets = exSnap.child("sets").getValue(Int::class.java) ?: 0,
                            repsOrDuration = exSnap.child("repsOrDuration").getValue(String::class.java) ?: "",
                            difficulty = exSnap.child("difficulty").getValue(String::class.java) ?: "",
                            restTimeSeconds = exSnap.child("restTimeSeconds").getValue(Int::class.java) ?: 30
                        )
                    )
                }
            }
            
            // Repopulate schedules
            if (snapshot.hasChild("schedules")) {
                val scSnap = snapshot.child("schedules")
                scSnap.children.forEach { sSnap ->
                    workoutDao.insertSchedule(
                        WorkoutScheduleEntity(
                            id = sSnap.child("id").getValue(Long::class.java) ?: 0L,
                            categoryId = sSnap.child("categoryId").getValue(Long::class.java) ?: 0L,
                            categoryName = sSnap.child("categoryName").getValue(String::class.java) ?: "",
                            hour = sSnap.child("hour").getValue(Int::class.java) ?: 8,
                            minute = sSnap.child("minute").getValue(Int::class.java) ?: 0,
                            daysOfWeek = sSnap.child("daysOfWeek").getValue(String::class.java) ?: "",
                            isActive = sSnap.child("isActive").getValue(Boolean::class.java) ?: true,
                            label = sSnap.child("label").getValue(String::class.java) ?: ""
                        )
                    )
                }
            }
            
            // Repopulate logs
            if (snapshot.hasChild("logs")) {
                val logsSnap = snapshot.child("logs")
                logsSnap.children.forEach { lSnap ->
                    workoutDao.insertLog(
                        WorkoutLogEntity(
                            id = lSnap.child("id").getValue(Long::class.java) ?: 0L,
                            exerciseId = lSnap.child("exerciseId").getValue(Long::class.java) ?: 0L,
                            exerciseName = lSnap.child("exerciseName").getValue(String::class.java) ?: "",
                            categoryName = lSnap.child("categoryName").getValue(String::class.java) ?: "",
                            completedTimestamp = lSnap.child("completedTimestamp").getValue(Long::class.java) ?: 0L,
                            note = lSnap.child("note").getValue(String::class.java) ?: "",
                            rating = lSnap.child("rating").getValue(Int::class.java) ?: 3,
                            durationSeconds = lSnap.child("durationSeconds").getValue(Int::class.java) ?: 0
                        )
                    )
                }
            }
            
            // Sync properties
            val categoriesCount = workoutDao.getAllCategoriesList().size
            val exercisesCount = workoutDao.getAllExercisesList().size
            val schedulesCount = workoutDao.getAllSchedulesList().size
            val logsCount = workoutDao.getAllLogsList().size
            val totalCount = categoriesCount + exercisesCount + schedulesCount + logsCount
            
            prefs.edit().apply {
                putInt("CLOUD_ITEMS_COUNT", totalCount)
                putLong("LAST_SYNC_TIME", System.currentTimeMillis())
                apply()
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Restore failed", e)
            return@withContext false
        }
    }

    fun getCloudSyncedItemsCount(): Int {
        return prefs.getInt("CLOUD_ITEMS_COUNT", 0)
    }

    private fun getFirebaseDatabase(): com.google.firebase.database.FirebaseDatabase {
        return try {
            com.google.firebase.database.FirebaseDatabase.getInstance("https://laplaingatquang-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance("https://reviewmind-spaced-default-rtdb.asia-southeast1.firebasedatabase.app")
            } catch (e2: Exception) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
            }
        }
    }
}
