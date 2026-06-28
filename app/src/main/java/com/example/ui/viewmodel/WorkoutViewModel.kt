package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.WorkoutRepository
import com.example.receiver.AlarmScheduler
import com.example.receiver.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WorkoutViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository: WorkoutRepository
    
    // UI Local state
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _cloudItemsCount = MutableStateFlow(0)
    val cloudItemsCount: StateFlow<Int> = _cloudItemsCount.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userName = MutableStateFlow("N/A")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow("N/A")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userAvatar = MutableStateFlow<String?>(null)
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    private val _autoSync = MutableStateFlow(true)
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    // Database Streams
    val categories: StateFlow<List<CategoryEntity>>
    val allExercises: StateFlow<List<ExerciseEntity>>
    val schedules: StateFlow<List<WorkoutScheduleEntity>>
    val logs: StateFlow<List<WorkoutLogEntity>>

    init {
        val database = WorkoutDatabase.getDatabase(application)
        repository = WorkoutRepository(database.workoutDao(), application)

        // Initialize streams safely using stateIn
        categories = repository.allCategories
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allExercises = repository.allExercises
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        schedules = repository.allSchedules
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        logs = repository.allLogs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Load sync status & settings
        _lastSyncTime.value = repository.getLastSyncTime()
        _cloudItemsCount.value = repository.getCloudSyncedItemsCount()
        _autoSync.value = repository.isAutoSyncEnabled()

        // Load Google Login Status
        updateLoginState()

        // Check if DB is empty and prepopulate on background thread
        viewModelScope.launch {
            repository.checkAndPrepopulate()
        }
    }

    private fun updateLoginState() {
        val loginInfo = repository.getUserLoginState()
        _isLoggedIn.value = loginInfo.first
        _userName.value = loginInfo.second.first
        _userEmail.value = loginInfo.second.second
        _userAvatar.value = loginInfo.second.third
    }

    // Selected Category Folder Exercises Flow
    val exercisesInSelectedCategory: StateFlow<List<ExerciseEntity>> = _selectedCategoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getExercisesByCategory(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    // --- Real Firebase Login & Registration Actions ---
    fun signInOrSignUpWithEmail(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val mAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val isSuccessful = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                    mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                continuation.resume(true) {}
                            } else {
                                // Attempt register as falling back
                                mAuth.createUserWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { signUpTask ->
                                        if (signUpTask.isSuccessful) {
                                            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                .setDisplayName(displayName)
                                                .build()
                                            signUpTask.result?.user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                                continuation.resume(true) {}
                                            } ?: continuation.resume(true) {}
                                        } else {
                                            continuation.resumeWith(Result.failure(signUpTask.exception ?: Exception("Đăng nhập/Đăng ký không thành công.")))
                                        }
                                    }
                            }
                        }
                }
                if (isSuccessful) {
                    onLoginCompletedSuccessful()
                }
            } catch (e: Exception) {
                Toast.makeText(application, "Lỗi đăng nhập: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isSyncing.value = false
                updateLoginState()
            }
        }
    }

    fun signInWithGoogleCredential(credential: com.google.firebase.auth.AuthCredential) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val mAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val isSuccessful = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                    mAuth.signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                continuation.resume(true) {}
                            } else {
                                continuation.resumeWith(Result.failure(task.exception ?: Exception("Lỗi đăng nhập Google Auth")))
                            }
                        }
                }
                if (isSuccessful) {
                    onLoginCompletedSuccessful()
                }
            } catch (e: Exception) {
                Toast.makeText(application, "Lỗi đăng nhập Google: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isSyncing.value = false
                updateLoginState()
            }
        }
    }

    private suspend fun onLoginCompletedSuccessful() {
        val state = repository.getUserLoginState()
        _isLoggedIn.value = state.first
        _userName.value = state.second.first
        _userEmail.value = state.second.second
        _userAvatar.value = state.second.third
        
        try {
            val didRestore = repository.restoreCloudSync()
            if (didRestore) {
                _lastSyncTime.value = repository.getLastSyncTime()
                _cloudItemsCount.value = repository.getCloudSyncedItemsCount()
                Toast.makeText(application, "Đã khôi phục toàn bộ tiến trình học tập từ đám mây của bạn!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(application, "Chào mừng! Đã kết nối đám mây thành công.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(application, "Kết nối thành công ở chế độ đệm ngoại tuyến.", Toast.LENGTH_LONG).show()
        }
    }

    fun loginWithGoogle(name: String, email: String, avatarUrl: String?) {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.saveUserLoginInfo(name, email, avatarUrl)
            updateLoginState()
            onLoginCompletedSuccessful()
            _isSyncing.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logoutUser()
            repository.clearAllLocalData() // ALWAYS clear local database completely on sign-out
            updateLoginState()
            Toast.makeText(application, "Đã đăng xuất tài khoản & xóa vùng đệm cục bộ.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Cloud Sync Simulated Actions ---
    fun triggerSync() {
        if (!_isLoggedIn.value) {
            Toast.makeText(application, "Vui lòng đăng nhập Google trước khi đồng bộ!", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val timestamp = repository.performCloudSync()
                _lastSyncTime.value = timestamp
                _cloudItemsCount.value = repository.getCloudSyncedItemsCount()
                Toast.makeText(application, "Đồng bộ dữ liệu đám mây thành công!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(application, "Đồng bộ thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        repository.setAutoSyncEnabled(enabled)
        _autoSync.value = enabled
    }

    // --- Database Operations with Auto Cloud Sync integration ---
    private fun triggerAutoSyncIfEnabled() {
        if (_isLoggedIn.value && _autoSync.value) {
            viewModelScope.launch {
                try {
                    repository.performCloudSync()
                    _lastSyncTime.value = repository.getLastSyncTime()
                    _cloudItemsCount.value = repository.getCloudSyncedItemsCount()
                } catch (e: Exception) {
                    android.util.Log.e("WorkoutViewModel", "Auto sync background failure: ${e.message}")
                }
            }
        }
    }

    // Categories (Study Topics)
    fun addTopic(
        name: String, 
        startDate: String, 
        folderName: String, 
        reviewTime: String,
        int1: Int = 1,
        int2: Int = 3,
        int3: Int = 7,
        int4: Int = 15,
        int5: Int = 30
    ) {
        viewModelScope.launch {
            val topic = CategoryEntity(
                name = name,
                startDate = startDate,
                description = "||||||",
                reviewsCompleted = 0,
                folderName = folderName,
                reviewTime = reviewTime,
                interval1 = int1,
                interval2 = int2,
                interval3 = int3,
                interval4 = int4,
                interval5 = int5
            )
            val topicId = repository.insertCategory(topic)
            
            // Auto schedule daily alarms for this topic based on reviewTime!
            try {
                val timeParts = reviewTime.split(":")
                val hr = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
                val min = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                
                addSchedule(
                    categoryId = topicId,
                    categoryName = name,
                    hour = hr,
                    minute = min,
                    daysOfWeek = "Thứ Hai, Thứ Ba, Thứ Tư, Thứ Năm, Thứ Sáu, Thứ Bảy, Chủ Nhật",
                    label = "🔔 Ôn tập định kỳ: $name"
                )
            } catch (e: Exception) {
                // Safely ignore parsing issues and proceed
            }
            
            triggerAutoSyncIfEnabled()
        }
    }

    fun addCategory(name: String, description: String, iconName: String, colorHex: String) {
        viewModelScope.launch {
            val topic = CategoryEntity(name = name, description = description, iconName = iconName, colorHex = colorHex)
            repository.insertCategory(topic)
            triggerAutoSyncIfEnabled()
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(category)
            triggerAutoSyncIfEnabled()
        }
    }

    fun updateTopicDetails(
        id: Long,
        name: String,
        startDate: String,
        folderName: String,
        reviewTime: String,
        int1: Int,
        int2: Int,
        int3: Int,
        int4: Int,
        int5: Int
    ) {
        viewModelScope.launch {
            val allCats = categories.value
            val existing = allCats.find { it.id == id }
            val updated = existing?.copy(
                name = name,
                startDate = startDate,
                folderName = folderName,
                reviewTime = reviewTime,
                interval1 = int1,
                interval2 = int2,
                interval3 = int3,
                interval4 = int4,
                interval5 = int5
            ) ?: CategoryEntity(
                id = id,
                name = name,
                startDate = startDate,
                folderName = folderName,
                reviewTime = reviewTime,
                interval1 = int1,
                interval2 = int2,
                interval3 = int3,
                interval4 = int4,
                interval5 = int5
            )
            repository.updateCategory(updated)
            
            // Also update any alarm schedule corresponding to this categoryId
            try {
                val timeParts = reviewTime.split(":")
                val hr = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
                val min = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                
                val currentSchedules = schedules.value
                val linkedSchedule = currentSchedules.find { it.categoryId == id }
                if (linkedSchedule != null) {
                    val updatedSchedule = linkedSchedule.copy(
                        categoryName = name,
                        hour = hr,
                        minute = min,
                        label = "🔔 Ôn tập định kỳ: $name"
                    )
                    repository.updateSchedule(updatedSchedule)
                    if (updatedSchedule.isActive) {
                        AlarmScheduler.scheduleWorkoutAlarm(application, updatedSchedule)
                    }
                } else {
                    addSchedule(
                        categoryId = id,
                        categoryName = name,
                        hour = hr,
                        minute = min,
                        daysOfWeek = "Thứ Hai, Thứ Ba, Thứ Tư, Thứ Năm, Thứ Sáu, Thứ Bảy, Chủ Nhật",
                        label = "🔔 Ôn tập định kỳ: $name"
                    )
                }
            } catch (e: Exception) {
                // Ignore schedule update errors
            }
            triggerAutoSyncIfEnabled()
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            triggerAutoSyncIfEnabled()
        }
    }

    fun updateTopicProgress(topicId: Long, completedCount: Int) {
        viewModelScope.launch {
            val topic = categories.value.find { it.id == topicId }
            if (topic != null) {
                val updated = topic.copy(reviewsCompleted = completedCount)
                repository.updateCategory(updated)
                triggerAutoSyncIfEnabled()
            }
        }
    }

    // Exercises
    fun addExercise(categoryId: Long, name: String, description: String, targetMuscle: String, sets: Int, repsOrDuration: String, difficulty: String, restTime: Int) {
        viewModelScope.launch {
            val exercise = ExerciseEntity(
                categoryId = categoryId,
                name = name,
                description = description,
                targetMuscle = targetMuscle,
                sets = sets,
                repsOrDuration = repsOrDuration,
                difficulty = difficulty,
                restTimeSeconds = restTime
            )
            repository.insertExercise(exercise)
            triggerAutoSyncIfEnabled()
        }
    }

    fun updateExercise(exercise: ExerciseEntity) {
        viewModelScope.launch {
            repository.updateExercise(exercise)
            triggerAutoSyncIfEnabled()
        }
    }

    fun deleteExercise(exercise: ExerciseEntity) {
        viewModelScope.launch {
            repository.deleteExercise(exercise)
            triggerAutoSyncIfEnabled()
        }
    }

    // Schedules (With AlarmManager integration)
    fun addSchedule(categoryId: Long, categoryName: String, hour: Int, minute: Int, daysOfWeek: String, label: String) {
        viewModelScope.launch {
            val schedule = WorkoutScheduleEntity(
                categoryId = categoryId,
                categoryName = categoryName,
                hour = hour,
                minute = minute,
                daysOfWeek = daysOfWeek,
                isActive = true,
                label = label
            )
            val id = repository.insertSchedule(schedule)
            val savedSchedule = schedule.copy(id = id)
            // Schedule actual alarm on system
            AlarmScheduler.scheduleWorkoutAlarm(application, savedSchedule)
            triggerAutoSyncIfEnabled()
        }
    }

    fun updateSchedule(schedule: WorkoutScheduleEntity) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
            if (schedule.isActive) {
                AlarmScheduler.scheduleWorkoutAlarm(application, schedule)
            } else {
                AlarmScheduler.cancelWorkoutAlarm(application, schedule)
            }
            triggerAutoSyncIfEnabled()
        }
    }

    fun toggleScheduleActive(schedule: WorkoutScheduleEntity, active: Boolean) {
        viewModelScope.launch {
            val updatedSchedule = schedule.copy(isActive = active)
            repository.updateSchedule(updatedSchedule)
            if (active) {
                AlarmScheduler.scheduleWorkoutAlarm(application, updatedSchedule)
            } else {
                AlarmScheduler.cancelWorkoutAlarm(application, updatedSchedule)
            }
            triggerAutoSyncIfEnabled()
        }
    }

    fun deleteSchedule(schedule: WorkoutScheduleEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancelWorkoutAlarm(application, schedule)
            repository.deleteSchedule(schedule)
            triggerAutoSyncIfEnabled()
        }
    }

    fun clearAllSchedules() {
        viewModelScope.launch {
            schedules.value.forEach { schedule ->
                AlarmScheduler.cancelWorkoutAlarm(application, schedule)
            }
            repository.clearAllSchedules()
            triggerAutoSyncIfEnabled()
        }
    }

    // Logs (History tracking)
    fun logCompletedWorkout(exerciseId: Long, exerciseName: String, categoryName: String, note: String, rating: Int, durationSeconds: Int) {
        viewModelScope.launch {
            val log = WorkoutLogEntity(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                categoryName = categoryName,
                note = note,
                rating = rating,
                durationSeconds = durationSeconds
            )
            repository.insertLog(log)
            triggerAutoSyncIfEnabled()
            Toast.makeText(application, "🎉 Đã ghi nhận lịch sử tập luyện!", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteLog(log: WorkoutLogEntity) {
        viewModelScope.launch {
            repository.deleteLog(log)
            triggerAutoSyncIfEnabled()
        }
    }

    // Trigger instant test notification
    fun sendTestNotification(categoryName: String, label: String) {
        val helper = NotificationHelper(application)
        helper.showWorkoutReminder(categoryName, label)
    }
}

// Factory
class WorkoutViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkoutViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
