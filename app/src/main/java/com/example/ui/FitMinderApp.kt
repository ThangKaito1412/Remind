package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CategoryEntity
import com.example.data.local.ExerciseEntity
import com.example.data.local.WorkoutLogEntity
import com.example.data.local.WorkoutScheduleEntity
import com.example.ui.viewmodel.WorkoutViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.launch
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.io.FileOutputStream

// --- Spaced Repetition Helpers & Constants ---
val repetitionIntervals = listOf(1, 3, 7, 15, 30)

fun saveChosenBackgroundImage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.filesDir, "custom_background.png")
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun getIntervalDaysForCategory(topic: CategoryEntity, index: Int): Int {
    return when (index) {
        0 -> topic.interval1
        1 -> topic.interval2
        2 -> topic.interval3
        3 -> topic.interval4
        4 -> topic.interval5
        else -> 30
    }
}

fun calculateReviewDate(topic: CategoryEntity, index: Int): Date {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val baseDate = try {
        sdf.parse(topic.startDate) ?: Date()
    } catch (e: Exception) {
        Date()
    }
    val cal = Calendar.getInstance().apply {
        time = baseDate
        add(Calendar.DAY_OF_YEAR, getIntervalDaysForCategory(topic, index))
    }
    return cal.time
}

data class IntervalConfig(
    val label: String,
    val value: Int,
    val onValueChange: (Int) -> Unit
)

data class ReviewStatus(
    val text: String,
    val badgeColor: Color,
    val textColor: Color,
    val relativeText: String,
    val isDueToday: Boolean,
    val isOverdue: Boolean,
    val isCompleted: Boolean,
    val daysRemaining: Int = 0
)

fun getTopicStatus(topic: CategoryEntity): ReviewStatus {
    if (topic.reviewsCompleted >= 5) {
        return ReviewStatus(
            text = "Đã hoàn thành!",
            badgeColor = Color(0xFFE8F5E9),
            textColor = Color(0xFF2E7D32),
            relativeText = "Đã ôn tập đủ 5/5 lần",
            isDueToday = false,
            isOverdue = false,
            isCompleted = true
        )
    }
    
    val nextReviewDate = calculateReviewDate(topic, topic.reviewsCompleted)
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val reviewCal = Calendar.getInstance().apply {
        time = nextReviewDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    
    val diffMs = reviewCal.timeInMillis - todayCal.timeInMillis
    val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()
    val nextNum = topic.reviewsCompleted + 1
    
    return when {
        diffDays == 0 -> {
            ReviewStatus(
                text = "Ôn tập hôm nay! (Lần $nextNum)",
                badgeColor = Color(0xFFFFEBEE),
                textColor = Color(0xFFC62828),
                relativeText = "Hạn chót hôm nay",
                isDueToday = true,
                isOverdue = false,
                isCompleted = false
            )
        }
        diffDays < 0 -> {
            ReviewStatus(
                text = "Trễ hạn! (Lần $nextNum)",
                badgeColor = Color(0xFFFFF3E0),
                textColor = Color(0xFFEF6C00),
                relativeText = "Trễ ${kotlin.math.abs(diffDays)} ngày",
                isDueToday = false,
                isOverdue = true,
                isCompleted = false
            )
        }
        else -> {
            ReviewStatus(
                text = "Còn $diffDays ngày (Lần $nextNum)",
                badgeColor = Color(0xFFE3F2FD),
                textColor = Color(0xFF1565C0),
                relativeText = "Ôn ngày " + SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(nextReviewDate),
                isDueToday = false,
                isOverdue = false,
                isCompleted = false,
                daysRemaining = diffDays
            )
        }
    }
}

fun getReviewNotesList(description: String): List<String> {
    val list = description.split("|||").map { it.trim() }
    val result = mutableListOf("", "", "", "", "")
    for (i in 0..4) {
        if (i < list.size) {
            result[i] = list[i]
        }
    }
    return result
}

fun serializeReviewNotes(notes: List<String>): String {
    return notes.joinToString("|||")
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FitMinderApp(
    viewModel: WorkoutViewModel,
    isDarkTheme: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    backgroundImagePath: String?,
    onBackgroundImageChanged: (String?) -> Unit
) {
    var currentTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    
    // Check if background notification marked something complete and requested to open Lucky Wheel
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("trigger_lucky_wheel_on_open", false)) {
            prefs.edit().putBoolean("trigger_lucky_wheel_on_open", false).apply()
            currentTab = 2 // Switch to Lucky Wheel tab!
        }
    }
    
    // Check if we need to show the Snooze Dialog
    var snoozeTopicId by remember { mutableStateOf<Long?>(null) }
    var snoozeTopicName by remember { mutableStateOf("") }
    var snoozeLabel by remember { mutableStateOf("") }
    
    LaunchedEffect(activity?.intent) {
        activity?.intent?.let { intent ->
            if (intent.hasExtra("SNOOZE_CATEGORY_ID")) {
                snoozeTopicId = intent.getLongExtra("SNOOZE_CATEGORY_ID", -1L)
                snoozeTopicName = intent.getStringExtra("SNOOZE_CATEGORY_NAME") ?: "Bài luyện tập"
                snoozeLabel = intent.getStringExtra("SNOOZE_LABEL") ?: "Lịch trình"
                intent.removeExtra("SNOOZE_CATEGORY_ID")
            }
            if (intent.hasExtra("NAVIGATE_TO_TOPIC_ID")) {
                val topicId = intent.getLongExtra("NAVIGATE_TO_TOPIC_ID", -1L)
                currentTab = 0 // Go to dashboard
                intent.removeExtra("NAVIGATE_TO_TOPIC_ID")
            }
        }
    }
    
    // States directly collected from the centralized study ViewModel
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userAvatar by viewModel.userAvatar.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val cloudItemsCount by viewModel.cloudItemsCount.collectAsStateWithLifecycle()
    val autoSync by viewModel.autoSync.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar(
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.School, contentDescription = "Trang chủ") },
                    label = { Text("Trang chủ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_home")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Alarm, contentDescription = "Nhắc nhở") },
                    label = { Text("Nhắc nhở", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_folders") // Backwards-compatible testTag
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Casino, contentDescription = "Vòng quay") },
                    label = { Text("Vòng quay", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_schedules") // Backwards-compatible testTag
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = "Đồng bộ") },
                    label = { Text("Cá nhân", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_sync") // Backwards-compatible testTag
                )
            }
        }
    ) { innerPadding ->
        val backgroundBitmap = remember(backgroundImagePath) {
            if (backgroundImagePath != null) {
                try {
                    BitmapFactory.decodeFile(backgroundImagePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (backgroundBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                )
                Image(
                    bitmap = backgroundBitmap,
                    contentDescription = "Ảnh nền cá nhân",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (isDarkTheme) 0.62f else 0.72f
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (isDarkTheme) {
                                    listOf(Color(0xFF0F172A), Color(0xFF020617))
                                } else {
                                    listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                }
                            )
                        )
                )
            }

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, delayMillis = 40)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220, delayMillis = 40)))
                        .togetherWith(fadeOut(animationSpec = tween(100)))
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> DashboardScreen(
                        viewModel = viewModel,
                        topics = categories,
                        logs = logs,
                        onNavigateToTab = { currentTab = it },
                        isDarkTheme = isDarkTheme
                    )
                    1 -> StudyScheduleScreen(
                        viewModel = viewModel,
                        topics = categories,
                        schedules = schedules,
                        isDarkTheme = isDarkTheme
                    )
                    2 -> StudyHistoryScreen(
                        viewModel = viewModel,
                        logs = logs,
                        isDarkTheme = isDarkTheme
                    )
                    3 -> SyncProfileScreen(
                        viewModel = viewModel,
                        isLoggedIn = isLoggedIn,
                        userName = userName,
                        userEmail = userEmail,
                        userAvatar = userAvatar,
                        isSyncing = isSyncing,
                        lastSyncTime = lastSyncTime,
                        cloudItemsCount = cloudItemsCount,
                        autoSync = autoSync,
                        isDarkTheme = isDarkTheme,
                        onDarkThemeChanged = onDarkThemeChanged,
                        backgroundImagePath = backgroundImagePath,
                        onBackgroundImageChanged = onBackgroundImageChanged
                    )
                }
            }
        }
    }

    // --- SNOOZE TIME PICKER DIALOG ---
    if (snoozeTopicId != null) {
        var customMinutesText by remember { mutableStateOf("") }
        
        Dialog(onDismissRequest = { snoozeTopicId = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "⏰ Nhắc Nhở Sau",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B)
                    )
                    
                    Text(
                        text = "Chọn thời gian nhắc lại cho bài tập '${snoozeTopicName}':",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                    
                    // Quick Options Row/Grid
                    val options = listOf(
                        "15 phút" to 15,
                        "30 phút" to 30,
                        "1 giờ" to 60,
                        "2 giờ" to 120,
                        "5 giờ" to 300
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (labelStr, mins) ->
                            OutlinedButton(
                                onClick = {
                                    com.example.receiver.AlarmScheduler.scheduleSnoozeAlarm(
                                        context = context,
                                        categoryId = snoozeTopicId!!,
                                        categoryName = snoozeTopicName,
                                        label = snoozeLabel,
                                        minutesFromNow = mins
                                    )
                                    Toast.makeText(context, "Sẽ nhắc lại sau $labelStr! ⏰", Toast.LENGTH_LONG).show()
                                    snoozeTopicId = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4F46E5))
                            ) {
                                Text(labelStr, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    
                    Divider(color = Color(0xFFE2E8F0))
                    
                    // Custom minutes input option
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Hoặc nhập số phút tùy chỉnh:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customMinutesText,
                                onValueChange = { customMinutesText = it.filter { char -> char.isDigit() } },
                                placeholder = { Text("Số phút (ví dụ: 45)", fontSize = 12.sp, color = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedBorderColor = Color(0xFF4F46E5),
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                )
                            )
                            
                            Button(
                                onClick = {
                                    val mins = customMinutesText.toIntOrNull()
                                    if (mins != null && mins > 0) {
                                        com.example.receiver.AlarmScheduler.scheduleSnoozeAlarm(
                                            context = context,
                                            categoryId = snoozeTopicId!!,
                                            categoryName = snoozeTopicName,
                                            label = snoozeLabel,
                                            minutesFromNow = mins
                                        )
                                        Toast.makeText(context, "Sẽ nhắc lại sau $mins phút! ⏰", Toast.LENGTH_LONG).show()
                                        snoozeTopicId = null
                                    } else {
                                        Toast.makeText(context, "Vui lòng nhập số phút hợp lệ!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                enabled = customMinutesText.isNotEmpty()
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    TextButton(
                        onClick = { snoozeTopicId = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Hủy", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Screen 1: DashboardScreen with Bento Grid & Spaced Repetition ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: WorkoutViewModel,
    topics: List<CategoryEntity>,
    logs: List<WorkoutLogEntity>,
    onNavigateToTab: (Int) -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog states
    var showAddTopicDialog by remember { mutableStateOf(false) }
    var showGeneralTimelineDialog by remember { mutableStateOf(false) }
    var selectedTopicForTimeline by remember { mutableStateOf<CategoryEntity?>(null) }
    var editingTopic by remember { mutableStateOf<CategoryEntity?>(null) }
    var notifSettingsTopic by remember { mutableStateOf<CategoryEntity?>(null) }
    
    // Filter index: 0 = Tất cả, 1 = Cần ôn hôm nay, 2 = Sắp tới, 3 = Đã hoàn thành
    var currentFilterIndex by remember { mutableStateOf(0) }
    
    // Calculate study streak days from logs
    val streakDays = remember(logs) {
        if (logs.isEmpty()) 0
        else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val uniqueDays = logs.map {
                sdf.format(Date(it.completedTimestamp))
            }.distinct().sortedDescending()
            
            var streak = 0
            val calendar = Calendar.getInstance()
            var compDayStr = sdf.format(calendar.time)
            
            // Check if user has finished today or yesterday to continue streak
            val todayStr = sdf.format(Date())
            val yesterdayStr = sdf.format(Date(System.currentTimeMillis() - 86400000))
            
            if (uniqueDays.contains(todayStr) || uniqueDays.contains(yesterdayStr)) {
                if (uniqueDays.contains(todayStr)) {
                    streak++
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    compDayStr = sdf.format(calendar.time)
                } else {
                    compDayStr = yesterdayStr
                }
                
                while (uniqueDays.contains(compDayStr)) {
                    streak++
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    compDayStr = sdf.format(calendar.time)
                }
            }
            streak
        }
    }

    // Determine next topic awaiting review
    val nextReviewTopic = remember(topics) {
        topics.filter { it.reviewsCompleted < 5 }
            .minByOrNull { calculateReviewDate(it, it.reviewsCompleted).time }
    }

    val activeFolders = remember(topics) {
        listOf("Tất cả thư mục") + topics.map { it.folderName }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    var selectedFolderName by remember { mutableStateOf("Tất cả thư mục") }

    // Filtered topics based on search constraints, folder selection and current selected tab
    val filteredTopics = remember(topics, currentFilterIndex, searchQuery, selectedFolderName) {
        val baseList = if (searchQuery.trim().isEmpty()) {
            topics
        } else {
            topics.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        
        val list = if (selectedFolderName == "Tất cả thư mục") {
            baseList
        } else {
            baseList.filter { it.folderName == selectedFolderName }
        }
        
        when (currentFilterIndex) {
            1 -> { // Cần ôn hôm nay (due today or overdue)
                list.filter {
                    val status = getTopicStatus(it)
                    status.isDueToday || status.isOverdue
                }
            }
            2 -> { // Sắp tới
                list.filter {
                    val status = getTopicStatus(it)
                    !status.isCompleted && !status.isDueToday && !status.isOverdue
                }
            }
            3 -> { // Đã hoàn thành (5/5 times)
                list.filter { it.reviewsCompleted >= 5 }
            }
            else -> list // Tất cả
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- BENTO GRID STYLE CELLS ---
        
        // cell 3: 3 Columns Split Grid (Streak, Total study, Sync state)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Streak Card
                Card(
                    modifier = Modifier
                        .weight(1.5f)
                        .height(105.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🔥 CHUỖI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$streakDays ngày", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (isDarkTheme) Color.White else Color(0xFF1E293B))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("liên tiếp", fontSize = 10.sp, color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B), textAlign = TextAlign.Center, maxLines = 1)
                    }
                }

                // Overdue Card
                Card(
                    modifier = Modifier
                        .weight(1.5f)
                        .height(105.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("⚠️ ĐÃ TRỄ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                        Spacer(modifier = Modifier.height(4.dp))
                        val overdueCount = topics.count { getTopicStatus(it).isOverdue }
                        Text("$overdueCount", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (overdueCount > 0) Color(0xFFEF4444) else (if (isDarkTheme) Color.White else Color(0xFF1E293B)))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("bài trễ hạn", fontSize = 10.sp, color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B), textAlign = TextAlign.Center, maxLines = 1)
                    }
                }

                // Today Card
                Card(
                    modifier = Modifier
                        .weight(1.7f)
                        .height(105.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📅 HÔM NAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        Spacer(modifier = Modifier.height(4.dp))
                        val dueTodayCount = topics.count { getTopicStatus(it).isDueToday }
                        Text("$dueTodayCount", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (dueTodayCount > 0) Color(0xFF10B981) else (if (isDarkTheme) Color.White else Color(0xFF1E293B)))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("bài cần ôn", fontSize = 10.sp, color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B), textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }

        // cell 2: Next Review Waiting Cell
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Gợi ý ôn tập",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GỢI Ý ÔN TẬP TIẾP THEO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (nextReviewTopic != null) {
                        val status = getTopicStatus(nextReviewTopic)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nextReviewTopic.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(status.badgeColor)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = status.text,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = status.textColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Lần ôn tiếp theo: Lần ${nextReviewTopic.reviewsCompleted + 1}",
                                        fontSize = 12.sp,
                                        color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    )
                                }
                            }
                            
                            Button(
                                onClick = {
                                    val nextCompleted = (nextReviewTopic.reviewsCompleted + 1).coerceAtMost(5)
                                    viewModel.updateTopicProgress(nextReviewTopic.id, nextCompleted)
                                    viewModel.logCompletedWorkout(
                                        exerciseId = nextReviewTopic.id,
                                        exerciseName = nextReviewTopic.name,
                                        categoryName = "Ôn Tập Lặp Lại",
                                        note = "Ôn nhanh từ Gợi ý ôn tập",
                                        rating = 5,
                                        durationSeconds = 600
                                    )
                                    
                                    // Award 1 lucky spin
                                    val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
                                    val currentSpins = prefs.getInt("available_spins", 0)
                                    prefs.edit().putInt("available_spins", currentSpins + 1).apply()
                                    
                                    Toast.makeText(context, "Đã ghi nhận ôn tập cho '${nextReviewTopic.name}'! Nhận 1 lượt quay! 🎁", Toast.LENGTH_LONG).show()
                                    
                                    // Navigate to Lucky Wheel tab (tab index 2)
                                    onNavigateToTab(2)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Đã ôn",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Đã ôn", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = "Tuyệt vời! Bạn không còn bài ôn tập trễ hạn.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                        )
                    }
                }
            }
        }

        // cell 4: Quick Action Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showAddTopicDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("add_topic_button"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Thêm",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Thêm Chủ Đề",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { showGeneralTimelineDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Timeline",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Timeline Chung",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Folder storage categories selection
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📁 Thư mục học tập (${activeFolders.size - 1})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeFolders.forEach { folder ->
                        item {
                            val isSelected = selectedFolderName == folder
                            val folderBg = if (isSelected) Color(0xFF4F46E5) else (if (isDarkTheme) Color(0xFF334155) else Color(0xFFF8FAFC))
                            val folderColor = if (isSelected) Color.White else (if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569))
                            val folderBorder = if (isSelected) Color(0xFF4F46E5) else (if (isDarkTheme) Color(0xFF475569) else Color(0xFFE2E8F0))
                            
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(folderBg)
                                    .border(1.dp, folderBorder, RoundedCornerShape(8.dp))
                                    .clickable { selectedFolderName = folder }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (folder == "Tất cả thư mục") Icons.Default.FolderOpen else Icons.Default.Folder,
                                    contentDescription = "Folder",
                                    tint = folderColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = folder,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = folderColor
                                )
                                if (folder != "Tất cả thư mục") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0x33FFFFFF) else (if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${topics.count { it.folderName == folder }}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else (if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Input Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color.White, RoundedCornerShape(12.dp)),
                placeholder = { Text("Tìm kiếm nhanh chủ đề...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                    unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                    focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                    unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                    focusedBorderColor = Color(0xFF4F46E5),
                    unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                )
            )
        }

        // Filter chips list
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chips = listOf("Tất Cả", "Cần Ôn Hôm Nay", "Sắp Tới", "Đã Hoàn Thành")
                chips.forEachIndexed { index, title ->
                    item {
                        val isSelected = currentFilterIndex == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { currentFilterIndex = index },
                            label = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4F46E5),
                                selectedLabelColor = Color.White,
                                containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                                labelColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF4F46E5) else (if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                            )
                        )
                    }
                }
            }
        }

        // List Header or notification empty states
        if (filteredTopics.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDarkTheme) Color(0xFF1E293B) else Color.White, RoundedCornerShape(16.dp))
                        .padding(vertical = 44.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Chưa có chủ đề",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (topics.isEmpty()) "Chưa có chủ đề nào được theo dõi" else "Không có chủ đề khớp bộ lọc này",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nhấp 'Thêm Chủ Đề' để thêm từ vựng, bài học cần nhắc nhở lặp lại.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Study cards list
            items(filteredTopics, key = { it.id }) { topic ->
                val status = getTopicStatus(topic)
                val scrollState = rememberScrollState()
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTopicForTimeline = topic },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFEEF2F6))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "📁 " + topic.folderName,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isDarkTheme) Color(0xFF451A03) else Color(0xFFFFF7ED))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "⏰ " + topic.reviewTime,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) Color(0xFFFB923C) else Color(0xFFEA580C)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = topic.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                val parsedBase = try {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(topic.startDate) ?: Date()
                                } catch(e: Exception) { Date() }
                                Text(
                                    text = "Bắt đầu học: " + sdfDisplay.format(parsedBase),
                                    fontSize = 11.sp,
                                    color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(status.badgeColor)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = status.text,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = status.textColor
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Progress statistics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tiến độ lặp lại: ${topic.reviewsCompleted}/5 lần ôn tập",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                            )
                            Text(
                                text = status.relativeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = status.textColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Styled progress bar
                        LinearProgressIndicator(
                            progress = { topic.reviewsCompleted / 5.0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (topic.reviewsCompleted >= 5) Color(0xFF10B981) else Color(0xFF4F46E5),
                            trackColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cài đặt thông báo Button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        notifSettingsTopic = topic
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Cài đặt thông báo",
                                    tint = if (isDarkTheme) Color(0xFFFB923C) else Color(0xFFEA580C),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cài đặt TB", fontSize = 12.sp, color = if (isDarkTheme) Color(0xFFFB923C) else Color(0xFFEA580C))
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))

                            // Sửa Button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        editingTopic = topic
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Sửa",
                                    tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sửa", fontSize = 12.sp, color = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5))
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.deleteCategory(topic)
                                        Toast.makeText(context, "Đã xóa chủ đề", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Xóa",
                                    tint = Color.Red.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xóa", fontSize = 12.sp, color = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS IMPLEMENTATION ---
    
    // 1. Add Topic Dialog
    if (showAddTopicDialog) {
        var topicName by remember { mutableStateOf("") }
        var folderName by remember { mutableStateOf("Mặc định") }
        var customNotificationNote by remember { mutableStateOf("") }
        var reviewHour by remember { mutableIntStateOf(8) }
        var reviewMinute by remember { mutableIntStateOf(0) }
        var reviewTimeStr by remember { mutableStateOf("08:00") }
        var studyDateString by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
        
        var int1 by remember { mutableIntStateOf(1) }
        var int2 by remember { mutableIntStateOf(3) }
        var int3 by remember { mutableIntStateOf(7) }
        var int4 by remember { mutableIntStateOf(15) }
        var int5 by remember { mutableIntStateOf(30) }
        var expandIntervals by remember { mutableStateOf(false) }
        
        var intervalType by remember { mutableStateOf("5_times") }
        var everyNDays by remember { mutableIntStateOf(2) }
        var specificReminderDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
        
        Dialog(onDismissRequest = { showAddTopicDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Thêm Chủ Đề Ôn Tập Mới",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = topicName,
                        onValueChange = { topicName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên chủ đề từ vựng / bài giảng") },
                        placeholder = { Text("Ví dụ: Từ vựng IELTS Unit 5") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customNotificationNote,
                        onValueChange = { customNotificationNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nội dung ghi chú thông báo (tùy chọn)") },
                        placeholder = { Text("Ví dụ: Bạn rất giỏi! Hãy học bài nào!") },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Thư mục lưu trữ") },
                        placeholder = { Text("Ví dụ: Tiếng Anh, Lập trình...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        )
                    )
                    
                    val folderSuggestions = remember(topics) {
                        topics.map { it.folderName }.filter { it.isNotEmpty() && it != "Mặc định" }.distinct().sorted()
                    }
                    if (folderSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gợi ý thư mục hiện có (Chạm để chọn):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            folderSuggestions.forEach { sugg ->
                                item {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFEEF2F6))
                                            .border(1.dp, if (isDarkTheme) Color(0xFF475569) else Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                            .clickable { folderName = sugg }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(sugg, fontSize = 11.sp, color = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Giờ nhắc hẹn ôn tập hàng ngày",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            .clickable {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hr, min ->
                                        reviewHour = hr
                                        reviewMinute = min
                                        reviewTimeStr = String.format("%02d:%02d", hr, min)
                                    },
                                    reviewHour,
                                    reviewMinute,
                                    true
                                ).show()
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = reviewTimeStr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                        )
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Chọn giờ", tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Ngày bắt đầu học",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            .clickable {
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val formattedMonth = String.format("%02d", month + 1)
                                        val formattedDay = String.format("%02d", day)
                                        studyDateString = "$year-$formattedMonth-$formattedDay"
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = studyDateString,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                        )
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Chọn ngày", tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Customizable repetition spacing
                    Text(
                        text = "⚙️ Cài đặt mốc khoảng cách lặp",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                            .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Option 1: 5 lần
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { intervalType = "5_times" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (intervalType == "5_times"),
                                onClick = { intervalType = "5_times" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4F46E5))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("Ôn tập lặp 5 lần (Mặc định)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkTheme) Color.White else Color(0xFF1E293B))
                                Text("Khoảng cách tăng dần (1, 3, 7, 15, 30 ngày) hoặc tùy chỉnh", fontSize = 11.sp, color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B))
                             }
                        }
                        
                        // Option 2: Mỗi ngày
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { intervalType = "every_day" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (intervalType == "every_day"),
                                onClick = { intervalType = "every_day" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4F46E5))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("Mỗi ngày", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkTheme) Color.White else Color(0xFF1E293B))
                                Text("Ôn tập liên tiếp hàng ngày", fontSize = 11.sp, color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B))
                            }
                        }

                        // Option 3: Sau mỗi n ngày
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { intervalType = "every_n_days" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (intervalType == "every_n_days"),
                                onClick = { intervalType = "every_n_days" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4F46E5))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("Sau mỗi n ngày", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkTheme) Color.White else Color(0xFF1E293B))
                                Text("Lặp lại định kỳ sau mỗi n ngày", fontSize = 11.sp, color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B))
                            }
                        }
                        
                        if (intervalType == "every_n_days") {
                            Row(
                                modifier = Modifier
                                    .padding(start = 36.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Số ngày (n): ", fontSize = 12.sp, color = if (isDarkTheme) Color.White else Color(0xFF475569))
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                        .background(if (isDarkTheme) Color(0xFF0F172A) else Color.White)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$everyNDays ngày", 
                                        fontSize = 12.sp, 
                                        fontWeight = FontWeight.Bold, 
                                        color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropUp, 
                                            contentDescription = "Tăng",
                                            tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { everyNDays += 1 }
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown, 
                                            contentDescription = "Giảm",
                                            tint = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { if (everyNDays > 1) everyNDays -= 1 }
                                        )
                                    }
                                }
                            }
                        }

                        // Option 4: Nhắc vào 1 ngày nhất định theo lịch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { intervalType = "specific_date" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (intervalType == "specific_date"),
                                onClick = { intervalType = "specific_date" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4F46E5))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("Nhắc vào 1 ngày nhất định theo lịch", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkTheme) Color.White else Color(0xFF1E293B))
                                Text("Chọn chính xác ngày cần nhắc nhở", fontSize = 11.sp, color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B))
                            }
                        }
                        
                        if (intervalType == "specific_date") {
                            Row(
                                modifier = Modifier
                                    .padding(start = 36.dp)
                                    .fillMaxWidth()
                                    .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                    .background(if (isDarkTheme) Color(0xFF0F172A) else Color.White)
                                    .clickable {
                                        val calendar = Calendar.getInstance()
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val formattedMonth = String.format("%02d", month + 1)
                                                val formattedDay = String.format("%02d", day)
                                                specificReminderDate = "$year-$formattedMonth-$formattedDay"
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = specificReminderDate,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                                )
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Chọn ngày", tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    
                    if (intervalType == "5_times") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandIntervals = !expandIntervals }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "✏️ Tùy chỉnh chi tiết khoảng cách (lần 1-5)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5)
                            )
                            Icon(
                                imageVector = if (expandIntervals) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand",
                                tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        if (expandIntervals) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    IntervalConfig("Lần 1", int1) { int1 = it },
                                    IntervalConfig("Lần 2", int2) { int2 = it },
                                    IntervalConfig("Lần 3", int3) { int3 = it },
                                    IntervalConfig("Lần 4", int4) { int4 = it },
                                    IntervalConfig("Lần 5", int5) { int5 = it }
                                ).forEach { cfg ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(cfg.label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                                .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFF8FAFC))
                                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${cfg.value} d", 
                                                fontSize = 9.sp, 
                                                fontWeight = FontWeight.Bold, 
                                                color = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.Center
                                            )
                                            Column {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropUp, 
                                                    contentDescription = "Lên",
                                                    tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { cfg.onValueChange(cfg.value + 1) }
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown, 
                                                    contentDescription = "Xuống",
                                                    tint = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B),
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { if (cfg.value > 1) cfg.onValueChange(cfg.value - 1) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddTopicDialog = false }) {
                            Text("Hủy", color = Color(0xFF64748B))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (topicName.trim().isEmpty()) {
                                    Toast.makeText(context, "Vui lòng nhập tên chủ đề", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val folderStr = if (folderName.trim().isEmpty()) "Mặc định" else folderName.trim()
                                
                                val finalInt1: Int
                                val finalInt2: Int
                                val finalInt3: Int
                                val finalInt4: Int
                                val finalInt5: Int
                                
                                when (intervalType) {
                                    "every_day" -> {
                                        finalInt1 = 1
                                        finalInt2 = 2
                                        finalInt3 = 3
                                        finalInt4 = 4
                                        finalInt5 = 5
                                    }
                                    "every_n_days" -> {
                                        val n = everyNDays
                                        finalInt1 = n
                                        finalInt2 = 2 * n
                                        finalInt3 = 3 * n
                                        finalInt4 = 4 * n
                                        finalInt5 = 5 * n
                                    }
                                    "specific_date" -> {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        val start = try { sdf.parse(studyDateString) ?: Date() } catch(e: Exception) { Date() }
                                        val target = try { sdf.parse(specificReminderDate) ?: Date() } catch(e: Exception) { Date() }
                                        val diff = target.time - start.time
                                        val diffDays = (diff / (1000 * 60 * 60 * 24)).toInt()
                                        val finalD = if (diffDays > 0) diffDays else 1
                                        
                                        finalInt1 = finalD
                                        finalInt2 = finalD
                                        finalInt3 = finalD
                                        finalInt4 = finalD
                                        finalInt5 = finalD
                                    }
                                    else -> { // "5_times"
                                        finalInt1 = int1
                                        finalInt2 = int2
                                        finalInt3 = int3
                                        finalInt4 = int4
                                        finalInt5 = int5
                                    }
                                }
                                
                                viewModel.addTopic(
                                    topicName.trim(), 
                                    studyDateString, 
                                    folderStr, 
                                    reviewTimeStr, 
                                    finalInt1, 
                                    finalInt2, 
                                    finalInt3, 
                                    finalInt4, 
                                    finalInt5,
                                    customNotificationNote.trim()
                                )
                                showAddTopicDialog = false
                                Toast.makeText(context, "Thêm chủ đề lặp thành công!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier.testTag("submit_button")
                        ) {
                            Text("Lưu Lịch Trình")
                        }
                    }
                }
            }
        }
    }

    // Edit Topic Dialog
    editingTopic?.let { topic ->
        var topicName by remember(topic) { mutableStateOf(topic.name) }
        var folderName by remember(topic) { mutableStateOf(topic.folderName) }
        val timeParts = remember(topic) { topic.reviewTime.split(":") }
        var reviewHour by remember(topic) { mutableIntStateOf(timeParts.getOrNull(0)?.toIntOrNull() ?: 8) }
        var reviewMinute by remember(topic) { mutableIntStateOf(timeParts.getOrNull(1)?.toIntOrNull() ?: 0) }
        var reviewTimeStr by remember(topic) { mutableStateOf(topic.reviewTime) }
        var studyDateString by remember(topic) { mutableStateOf(topic.startDate) }
        
        var int1 by remember(topic) { mutableIntStateOf(topic.interval1) }
        var int2 by remember(topic) { mutableIntStateOf(topic.interval2) }
        var int3 by remember(topic) { mutableIntStateOf(topic.interval3) }
        var int4 by remember(topic) { mutableIntStateOf(topic.interval4) }
        var int5 by remember(topic) { mutableIntStateOf(topic.interval5) }
        var expandIntervals by remember { mutableStateOf(false) }
        
        Dialog(onDismissRequest = { editingTopic = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Chỉnh Sửa Chủ Đề Ôn Tập",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = topicName,
                        onValueChange = { topicName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên chủ đề từ vựng / bài giảng") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Thư mục lưu trữ") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        )
                    )
                    
                    val folderSuggestions = remember(topics) {
                        topics.map { it.folderName }.filter { it.isNotEmpty() && it != "Mặc định" }.distinct().sorted()
                    }
                    if (folderSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gợi ý thư mục hiện có (Chạm để chọn):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            folderSuggestions.forEach { sugg ->
                                item {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFEEF2F6))
                                            .border(1.dp, if (isDarkTheme) Color(0xFF475569) else Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                            .clickable { folderName = sugg }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(sugg, fontSize = 11.sp, color = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Giờ nhắc hẹn ôn tập hàng ngày",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            .clickable {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hr, min ->
                                        reviewHour = hr
                                        reviewMinute = min
                                        reviewTimeStr = String.format("%02d:%02d", hr, min)
                                    },
                                    reviewHour,
                                    reviewMinute,
                                    true
                                ).show()
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = reviewTimeStr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                        )
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Chọn giờ", tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Ngày bắt đầu học",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            .clickable {
                                val dateParts = studyDateString.split("-")
                                val yr = dateParts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                val mo = (dateParts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                                val dy = dateParts.getOrNull(2)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val formattedMonth = String.format("%02d", month + 1)
                                        val formattedDay = String.format("%02d", day)
                                        studyDateString = "$year-$formattedMonth-$formattedDay"
                                    },
                                    yr,
                                    mo,
                                    dy
                                ).show()
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = studyDateString,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                        )
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Chọn ngày", tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Customizable repetition spacing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandIntervals = !expandIntervals }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚙️ Chỉnh sửa mốc khoảng cách lặp (lần 1-5)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5)
                        )
                        Icon(
                            imageVector = if (expandIntervals) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    if (expandIntervals) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                IntervalConfig("Lần 1", int1) { int1 = it },
                                IntervalConfig("Lần 2", int2) { int2 = it },
                                IntervalConfig("Lần 3", int3) { int3 = it },
                                IntervalConfig("Lần 4", int4) { int4 = it },
                                IntervalConfig("Lần 5", int5) { int5 = it }
                            ).forEach { cfg ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(cfg.label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                            .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFF8FAFC))
                                            .padding(horizontal = 2.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${cfg.value} d", 
                                            fontSize = 9.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Center
                                        )
                                        Column {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropUp, 
                                                contentDescription = "Lên",
                                                tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { cfg.onValueChange(cfg.value + 1) }
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown, 
                                                contentDescription = "Xuống",
                                                tint = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B),
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { if (cfg.value > 1) cfg.onValueChange(cfg.value - 1) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { editingTopic = null }) {
                            Text("Hủy", color = Color(0xFF64748B))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (topicName.trim().isEmpty()) {
                                    Toast.makeText(context, "Vui lòng nhập tên chủ đề", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val folderStr = if (folderName.trim().isEmpty()) "Mặc định" else folderName.trim()
                                viewModel.updateTopicDetails(
                                    topic.id,
                                    topicName.trim(),
                                    studyDateString,
                                    folderStr,
                                    reviewTimeStr,
                                    int1,
                                    int2,
                                    int3,
                                    int4,
                                    int5
                                )
                                editingTopic = null
                                Toast.makeText(context, "Cập nhật chủ đề thành công!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Text("Cập Nhật")
                        }
                    }
                }
            }
        }
    }

    // 2. Single Topic Detailed Timeline Dialog
    selectedTopicForTimeline?.let { topicPlaceholder ->
        val topic = topics.find { it.id == topicPlaceholder.id } ?: topicPlaceholder
        var tempNotesList by remember(topic.id) { mutableStateOf(getReviewNotesList(topic.description)) }
        val focusManager = LocalFocusManager.current

        Dialog(onDismissRequest = { selectedTopicForTimeline = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topic.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Dòng thời gian 5 cột mốc",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        IconButton(onClick = { selectedTopicForTimeline = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray)
                        }
                    }
                    
                    Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 10.dp))
                    
                    // Stepper Vertical Steppes
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val intervals = listOf(topic.interval1, topic.interval2, topic.interval3, topic.interval4, topic.interval5)
                            intervals.forEachIndexed { index, days ->
                                item {
                                    val reviewDate = calculateReviewDate(topic, index)
                                    val isCompleted = index < topic.reviewsCompleted
                                    val isNext = index == topic.reviewsCompleted
                                    
                                    val stepBg = when {
                                        isCompleted -> Color(0xFFE8F5E9)
                                        isNext -> Color(0xFFE3F2FD)
                                        else -> Color.White
                                    }
                                    val borderClr = when {
                                        isCompleted -> Color(0xFF81C784)
                                        isNext -> Color(0xFF64B5F6)
                                        else -> Color(0xFFE2E8F0)
                                    }
                                    val stepIcon = when {
                                        isCompleted -> Icons.Default.CheckCircle
                                        isNext -> Icons.Default.PlayCircle
                                        else -> Icons.Default.Schedule
                                    }
                                    val stepIconColor = when {
                                        isCompleted -> Color(0xFF2E7D32)
                                        isNext -> Color(0xFF1565C0)
                                        else -> Color(0xFF94A3B8)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(stepBg, RoundedCornerShape(12.dp))
                                            .border(1.dp, borderClr, RoundedCornerShape(12.dp))
                                            .padding(14.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = stepIcon,
                                                        contentDescription = "Step",
                                                        tint = stepIconColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = "Lần ${index + 1} (+${days} ngày)",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1E293B),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(reviewDate),
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.width(6.dp))
                                                
                                                if (isCompleted) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFC8E6C9))
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Đã ôn", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            // Increment reviewsCompleted & log completion history
                                                            val nextCompleted = index + 1
                                                            viewModel.updateTopicProgress(topic.id, nextCompleted)
                                                            viewModel.logCompletedWorkout(
                                                                exerciseId = topic.id,
                                                                exerciseName = topic.name,
                                                                categoryName = "Ôn Tập Lặp Lại",
                                                                note = tempNotesList.getOrElse(index) { "" },
                                                                rating = 3,
                                                                durationSeconds = 600
                                                            )
                                                            // Award 1 lucky spin
                                                            val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
                                                            val currentSpins = prefs.getInt("available_spins", 0)
                                                            prefs.edit().putInt("available_spins", currentSpins + 1).apply()
                                                            
                                                            Toast.makeText(context, "Đã ghi nhận ôn tập lần $nextCompleted! Nhận 1 lượt quay! 🎁", Toast.LENGTH_LONG).show()
                                                            
                                                            // Close dialog and switch to Lucky Wheel
                                                            selectedTopicForTimeline = null
                                                            onNavigateToTab(2)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = if (isNext) Color(0xFF2E7D32) else Color(0xFF475569)),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        modifier = Modifier.defaultMinSize(minWidth = 75.dp, minHeight = 28.dp),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isNext) "Báo đã xong" else "Xong sớm",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // Text area notes mapping
                                            var currentNoteText by remember(tempNotesList) { mutableStateOf(tempNotesList.getOrElse(index) { "" }) }
                                            val hasTextChanged = currentNoteText != tempNotesList.getOrElse(index) { "" }
                                            
                                            OutlinedTextField(
                                                value = currentNoteText,
                                                onValueChange = { currentNoteText = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(60.dp),
                                                placeholder = { Text("Ghi chú tiến độ, lỗi sai ôn tập...", fontSize = 11.sp) },
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color(0xFF1E293B),
                                                    unfocusedTextColor = Color(0xFF1E293B),
                                                    focusedBorderColor = Color(0xFF4F46E5),
                                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                )
                                            )
                                            
                                            if (hasTextChanged) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            val updatedList = tempNotesList.toMutableList()
                                                            updatedList[index] = currentNoteText.trim()
                                                            tempNotesList = updatedList
                                                            
                                                            val serialized = serializeReviewNotes(updatedList)
                                                            viewModel.updateCategory(topic.copy(description = serialized))
                                                            focusManager.clearFocus()
                                                            Toast.makeText(context, "Đã lưu ghi chú cho Lần ${index + 1}!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(0.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Save, contentDescription = "Lưu", modifier = Modifier.size(12.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Lưu ghi chú", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. General Calendar Timeline Grouped Dialog
    if (showGeneralTimelineDialog) {
        Dialog(onDismissRequest = { showGeneralTimelineDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Timeline Lịch Trình Chung",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        IconButton(onClick = { showGeneralTimelineDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray)
                        }
                    }
                    
                    Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 10.dp))
                    
                    // Group topics by due date
                    val calendarEvents = remember(topics) {
                        val map = mutableMapOf<String, MutableList<Pair<CategoryEntity, Int>>>()
                        topics.forEach { topic ->
                            val intervals = listOf(topic.interval1, topic.interval2, topic.interval3, topic.interval4, topic.interval5)
                            intervals.forEachIndexed { index, _ ->
                                val due = calculateReviewDate(topic, index)
                                val key = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(due)
                                if (!map.containsKey(key)) {
                                    map[key] = mutableListOf()
                                }
                                map[key]?.add(Pair(topic, index))
                            }
                        }
                        // Sort Map by date key
                        map.toSortedMap()
                    }
                    
                    if (calendarEvents.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Chưa có mốc lặp nào được tạo", fontSize = 14.sp, color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            calendarEvents.forEach { (dateKey, eventsList) ->
                                val parsedDate = try {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey) ?: Date()
                                } catch (e: Exception) { Date() }
                                
                                val isTodayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(parsedDate) == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                
                                item {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isTodayDate) Color(0xFFEEF2FF) else Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                                .padding(vertical = 6.dp, horizontal = 10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val dateFormatted = try {
                                                    SimpleDateFormat("EEEE, dd/MM", Locale("vi")).format(parsedDate)
                                                } catch (e: Exception) {
                                                    SimpleDateFormat("EEEE, dd/MM", Locale.getDefault()).format(parsedDate)
                                                }
                                                Text(
                                                    text = dateFormatted,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isTodayDate) Color(0xFF4F46E5) else Color(0xFF1E293B),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isTodayDate) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Hôm nay", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        eventsList.forEach { (topic, stepIdx) ->
                                            val completed = stepIdx < topic.reviewsCompleted
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = "Status",
                                                    tint = if (completed) Color(0xFF10B981) else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = topic.name,
                                                    fontSize = 12.sp,
                                                    color = if (completed) Color.Gray else Color(0xFF0F172A),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Lần ${stepIdx + 1}",
                                                    fontSize = 10.sp,
                                                    color = Color.Gray,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- NOTIFICATION SETTINGS MOTIVATIONAL MESSAGE DIALOG ---
    if (notifSettingsTopic != null) {
        val topic = notifSettingsTopic!!
        val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
        var customNote by remember { mutableStateOf(prefs.getString("notif_note_${topic.id}", "") ?: "") }
        
        Dialog(onDismissRequest = { notifSettingsTopic = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🔔 Ghi Chú Thông Báo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B)
                    )
                    
                    Text(
                        text = "Nhập ghi chú riêng để tạo động lực hoặc lời nhắc nhỏ khi đến giờ học bài tập này:",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                    
                    OutlinedTextField(
                        value = customNote,
                        onValueChange = { customNote = it },
                        placeholder = { Text("Ví dụ: Nhớ học từ vựng tiếng Anh nhé! Bạn rất giỏi!", fontSize = 12.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { notifSettingsTopic = null }) {
                            Text("Hủy", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                prefs.edit().putString("notif_note_${topic.id}", customNote).apply()
                                Toast.makeText(context, "Đã lưu ghi chú cho '${topic.name}'!", Toast.LENGTH_SHORT).show()
                                notifSettingsTopic = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Text("Lưu", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// --- Screen 2: StudyScheduleScreen (Alert notification lists) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScheduleScreen(
    viewModel: WorkoutViewModel,
    topics: List<CategoryEntity>,
    schedules: List<WorkoutScheduleEntity>,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    var showAddSchedule by remember { mutableStateOf(false) }
    var editingScheduleState by remember { mutableStateOf<WorkoutScheduleEntity?>(null) }
    
    // Schedule form states
    var selectedTopicId by remember { mutableStateOf<Long>(0L) }
    var hour by remember { mutableIntStateOf(18) }
    var minute by remember { mutableIntStateOf(0) }
    var notificationLabel by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hẹn Giờ Ôn Tập",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                        )
                        Text(
                            text = "Thiết lập hệ thống báo thức nhắc nhở học bài.",
                            fontSize = 12.sp,
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (schedules.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearAllSchedules()
                                    Toast.makeText(context, "Đã xóa toàn bộ nhắc nhở!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                border = BorderStroke(1.dp, Color.Red),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Xóa hết", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xóa hết", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                if (topics.isEmpty()) {
                                    Toast.makeText(context, "Vui lòng tạo chủ đề học tập trước!", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedTopicId = topics.first().id
                                    notificationLabel = "Ôn bài " + topics.first().name
                                    showAddSchedule = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Thêm", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mới", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (schedules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDarkTheme) Color(0xFF1E293B) else Color.White, RoundedCornerShape(12.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "Không có",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Chưa cài nhắc lịch định kỳ", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color(0xFF475569))
                            Text("Lịch hẹn sẽ đẩy đẩy thông báo đúng giờ", fontSize = 11.sp, color = if (isDarkTheme) Color(0xFF94A3B8) else Color.Gray)
                        }
                    }
                }
            } else {
                items(schedules, key = { it.id }) { schedule ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Light,
                                            contentDescription = "Active",
                                            tint = if (schedule.isActive) Color(0xFF4F46E5) else Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = schedule.label,
                                            fontSize = 11.sp,
                                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = String.format("%02d:%02d", schedule.hour, schedule.minute),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                                        )
                                }
                                
                                Switch(
                                    checked = schedule.isActive,
                                    onCheckedChange = { viewModel.toggleScheduleActive(schedule, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF4F46E5)
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chủ đề: ${schedule.categoryName}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF4F46E5),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Row {
                                    IconButton(
                                        onClick = { editingScheduleState = schedule },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Sửa",
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.deleteSchedule(schedule) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Xóa",
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // System Notification test triggers
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x104F46E5)),
                    border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "💡 Thử nghiệm kiểm tra hệ thống",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4338CA)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gửi thông báo giả lập ngay lập tức để kiểm chứng quyền đẩy tin nhắn.",
                            fontSize = 11.sp,
                            color = Color(0xFF4F46E5)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.sendTestNotification(
                                    categoryName = "Từ vựng IELTS",
                                    label = "Hạn ôn tập lặp lại ngay bây giờ!"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Gửi Báo Thử Ngay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Add Schedule Dialog picker
        if (showAddSchedule) {
            Dialog(onDismissRequest = { showAddSchedule = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Đặt Giờ Thông Báo Ôn Bài",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Topic selector spinner emulation
                        Text(
                            text = "Chọn mốc liên kết chủ đề",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val matchedTopic = topics.find { it.id == selectedTopicId } ?: topics.firstOrNull()
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                    .clickable { dropdownExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = matchedTopic?.name ?: "Vui lòng chọn...",
                                    fontSize = 14.sp,
                                    color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = if (isDarkTheme) Color.White else Color.Black
                                )
                            }
                            
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(if (isDarkTheme) Color(0xFF1E293B) else Color.White)
                            ) {
                                topics.forEach { topic ->
                                    DropdownMenuItem(
                                        text = { Text(topic.name, color = if (isDarkTheme) Color.White else Color.Black) },
                                        onClick = {
                                            selectedTopicId = topic.id
                                            notificationLabel = "Báo ôn lịch " + topic.name
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Time selector picker button
                        Text(
                            text = "Chọn giờ nhắc nhở",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, selectedHour, selectedMinute ->
                                            hour = selectedHour
                                            minute = selectedMinute
                                        },
                                        hour,
                                        minute,
                                        true
                                    ).show()
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format("%02d:%02d", hour, minute),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                            )
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Chọn giờ", tint = Color(0xFF4F46E5))
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddSchedule = false }) {
                                Text("Hủy", color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val finalTopic = topics.find { it.id == selectedTopicId } ?: topics.first()
                                    viewModel.addSchedule(
                                        categoryId = finalTopic.id,
                                        categoryName = finalTopic.name,
                                        hour = hour,
                                        minute = minute,
                                        daysOfWeek = "Thứ Hai, Thứ Ba, Thứ Tư, Thứ Năm, Thứ Sáu, Thứ Bảy, Chủ Nhật",
                                        label = notificationLabel
                                    )
                                    showAddSchedule = false
                                    Toast.makeText(context, "Đã lưu lịch nhắc nhở!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Text("Cài Nhắc")
                            }
                        }
                    }
                }
            }
        }

        editingScheduleState?.let { schedule ->
            var editSelectedTopicId by remember(schedule) { mutableStateOf(schedule.categoryId) }
            var editHour by remember(schedule) { mutableIntStateOf(schedule.hour) }
            var editMinute by remember(schedule) { mutableIntStateOf(schedule.minute) }
            var editNotificationLabel by remember(schedule) { mutableStateOf(schedule.label) }

            Dialog(onDismissRequest = { editingScheduleState = null }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Chỉnh Sửa Giờ Nhắc Nhở",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Chọn mốc liên kết chủ đề",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val matchedTopic = topics.find { it.id == editSelectedTopicId } ?: topics.firstOrNull()
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                    .clickable { dropdownExpanded = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = matchedTopic?.name ?: "Vui lòng chọn...",
                                    fontSize = 14.sp,
                                    color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = if (isDarkTheme) Color.White else Color.Black
                                )
                            }
                            
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(if (isDarkTheme) Color(0xFF1E293B) else Color.White)
                            ) {
                                topics.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.name, color = if (isDarkTheme) Color.White else Color.Black) },
                                        onClick = {
                                            editSelectedTopicId = t.id
                                            editNotificationLabel = "Báo ôn lịch " + t.name
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Chọn giờ nhắc nhở",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, selectedHour, selectedMinute ->
                                            editHour = selectedHour
                                            editMinute = selectedMinute
                                        },
                                        editHour,
                                        editMinute,
                                        true
                                    ).show()
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format("%02d:%02d", editHour, editMinute),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                            )
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Chọn giờ", tint = Color(0xFF4F46E5))
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { editingScheduleState = null }) {
                                Text("Hủy", color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val finalTopic = topics.find { it.id == editSelectedTopicId } ?: topics.first()
                                    viewModel.updateSchedule(
                                        schedule.copy(
                                            categoryId = finalTopic.id,
                                            categoryName = finalTopic.name,
                                            hour = editHour,
                                            minute = editMinute,
                                            label = editNotificationLabel
                                        )
                                    )
                                    editingScheduleState = null
                                    Toast.makeText(context, "Đã cập nhật giờ nhắc nhở!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Text("Lưu")
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Screen 3: LuckyWheelScreen ---
@Composable
fun StudyHistoryScreen(
    viewModel: WorkoutViewModel,
    logs: List<WorkoutLogEntity>,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Load rewards and spins
    var rewards by remember { mutableStateOf(loadRewards(context)) }
    var availableSpins by remember { mutableIntStateOf(0) }
    
    // States for editing a reward
    var editingRewardIndex by remember { mutableStateOf<Int?>(null) }
    var editingRewardText by remember { mutableStateOf("") }
    
    // Dynamic loading of spins
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
        // If first launch, give 1 free spin!
        if (!prefs.contains("available_spins")) {
            prefs.edit().putInt("available_spins", 1).apply()
        }
        availableSpins = prefs.getInt("available_spins", 0)
    }
    
    // Polling / listening for spin updates
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // Simple polling/refresh when screen is resumed to update spins awarded in background notifications
        while (true) {
            val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
            availableSpins = prefs.getInt("available_spins", 0)
            kotlinx.coroutines.delay(1000)
        }
    }
    
    var isSpinning by remember { mutableStateOf(false) }
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    var winnerName by remember { mutableStateOf("") }
    var showCelebration by remember { mutableStateOf(false) }
    var newRewardText by remember { mutableStateOf("") }
    
    // Visual Palette for segments
    val segmentColors = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFFEF4444), // Rose
        Color(0xFF8B5CF6), // Purple
        Color(0xFF06B6D4), // Cyan
        Color(0xFFEC4899), // Pink
        Color(0xFF14B8A6)  // Teal
    )

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Header
            Text(
                text = "🎁 VÒNG QUAY MAY MẮN",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4F46E5)
            )
            
            Text(
                text = "Hoàn thành bài ôn tập để nhận lượt quay và đổi lấy những phần thưởng động lực!",
                fontSize = 12.sp,
                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            
            // Available Spins Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                border = BorderStroke(1.5.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Lượt quay khả dụng của bạn",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🎁 Ôn tập chăm chỉ nhận thêm quà",
                            fontSize = 10.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFEEF2F6))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "$availableSpins lượt",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6366F1)
                        )
                    }
                }
            }
            
            // --- DRAW LUCKY WHEEL ---
            Box(
                modifier = Modifier
                    .size(290.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (rewards.isNotEmpty()) {
                    val sweepAngle = 360f / rewards.size
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = size.width / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        
                        // Draw outer elegant shadow-border
                        drawCircle(
                            color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0),
                            radius = radius + 6.dp.toPx()
                        )
                        drawCircle(
                            color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            radius = radius + 2.dp.toPx()
                        )
                        
                        // Draw segment arcs
                        for (i in rewards.indices) {
                            val startAngle = i * sweepAngle + rotation.value
                            drawArc(
                                color = segmentColors[i % segmentColors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true,
                                size = size
                            )
                        }
                        
                        // Draw segment text labels (native canvas rotation)
                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas
                            for (i in rewards.indices) {
                                val midAngle = i * sweepAngle + sweepAngle / 2f + rotation.value
                                nativeCanvas.save()
                                nativeCanvas.translate(center.x, center.y)
                                nativeCanvas.rotate(midAngle)
                                
                                val text = rewards[i]
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 11.dp.toPx()
                                    isFakeBoldText = true
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                                
                                // Draw text aligned from the outer radius edge inward
                                nativeCanvas.drawText(text, radius - 14.dp.toPx(), 4.dp.toPx(), paint)
                                nativeCanvas.restore()
                            }
                        }
                        
                        // Center decorative pivot pin
                        drawCircle(
                            color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            radius = 20.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFF4F46E5),
                            radius = 12.dp.toPx(),
                            center = center
                        )
                    }
                    
                    // Top Marker Pointer Arrow (Pointing downwards at 270 degrees)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-10).dp)
                            .size(24.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width / 2f, size.height)
                                lineTo(0f, 0f)
                                lineTo(size.width, 0f)
                                close()
                            }
                            drawPath(path = path, color = Color(0xFFEF4444))
                        }
                    }
                } else {
                    Text("Vui lòng thêm phần thưởng để bắt đầu quay!", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
            
            // Spin Trigger Button
            Button(
                onClick = {
                    if (availableSpins <= 0) {
                        Toast.makeText(context, "Bạn không có đủ lượt quay! Hãy hoàn thành ôn tập trước nhé!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isSpinning) return@Button
                    if (rewards.isEmpty()) return@Button
                    
                    scope.launch {
                        isSpinning = true
                        
                        val winnerIndex = java.util.Random().nextInt(rewards.size)
                        val sectorAngle = 360f / rewards.size
                        val targetSectorCenterAngle = winnerIndex * sectorAngle + sectorAngle / 2f
                        
                        // We align winner under the 270 degrees marker
                        val stopAngle = 270f - targetSectorCenterAngle
                        val totalRotationNeeded = 360f * 6 + stopAngle
                        
                        rotation.snapTo(rotation.value % 360f)
                        rotation.animateTo(
                            targetValue = totalRotationNeeded,
                            animationSpec = tween(
                                durationMillis = 3500,
                                easing = FastOutSlowInEasing
                            )
                        )
                        
                        winnerName = rewards[winnerIndex]
                        showCelebration = true
                        
                        // Decrement spin count
                        availableSpins = (availableSpins - 1).coerceAtLeast(0)
                        val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putInt("available_spins", availableSpins).apply()
                        
                        isSpinning = false
                    }
                },
                enabled = !isSpinning && rewards.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    disabledContainerColor = Color(0xFF94A3B8)
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Quay", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSpinning) "ĐANG QUAY..." else "QUAY NGAY!",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
            
            // --- MANAGE REWARDS SECTION ---
            Text(
                text = "🛠️ QUẢN LÝ PHẦN THƯỞNG",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else Color(0xFF334155),
                modifier = Modifier.align(Alignment.Start)
            )
            
            // Add Reward input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newRewardText,
                    onValueChange = { newRewardText = it },
                    placeholder = { Text("Nhập quà mới (ví dụ: Ly nước, 10p chơi game...)", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                        unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                        focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                        unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                        focusedBorderColor = Color(0xFF4F46E5),
                        unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                    )
                )
                
                Button(
                    onClick = {
                        val cleaned = newRewardText.trim()
                        if (cleaned.isNotEmpty()) {
                            if (rewards.size >= 12) {
                                Toast.makeText(context, "Chỉ nên thêm tối đa 12 phần thưởng để vòng quay được hiển thị đẹp nhất!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val updatedList = rewards.toMutableList().apply { add(cleaned) }
                            rewards = updatedList
                            saveRewards(context, updatedList)
                            newRewardText = ""
                            Toast.makeText(context, "Đã thêm phần thưởng!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("Thêm", fontWeight = FontWeight.Bold)
                }
            }
            
            // List of existing rewards
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rewards.forEachIndexed { index, reward ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (index % 2 == 0) (if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)) else (if (isDarkTheme) Color(0xFF1E293B) else Color.White), RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(segmentColors[index % segmentColors.size]),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${index + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = reward,
                                    fontSize = 13.sp,
                                    color = if (isDarkTheme) Color.White else Color(0xFF334155),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        editingRewardIndex = index
                                        editingRewardText = reward
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Sửa",
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                IconButton(
                                    onClick = {
                                        if (rewards.size <= 2) {
                                            Toast.makeText(context, "Vòng quay cần tối thiểu 2 phần thưởng để hoạt động!", Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        val updatedList = rewards.toMutableList().apply { removeAt(index) }
                                        rewards = updatedList
                                        saveRewards(context, updatedList)
                                        Toast.makeText(context, "Đã xóa phần thưởng", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Xóa",
                                        tint = Color.Red.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // --- CELEBRATION WINNER DIALOG ---
    if (showCelebration) {
        Dialog(onDismissRequest = { showCelebration = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                border = BorderStroke(2.dp, Color(0xFFF59E0B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🎉 CHÚC MỪNG BẠN! 🎉",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFEA580C)
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎁", fontSize = 56.sp)
                    }
                    
                    Text(
                        text = "Phần thưởng bạn nhận được là:",
                        fontSize = 13.sp,
                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFFFFBEB), RoundedCornerShape(12.dp))
                            .border(1.dp, if (isDarkTheme) Color(0xFF475569) else Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = winnerName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFFD97706),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Text(
                        text = "Hãy tận hưởng phần thưởng xứng đáng này sau buổi học tập hiệu quả nhé!",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    
                    Button(
                        onClick = { showCelebration = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Nhận thưởng!", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // --- EDIT REWARD DIALOG ---
    if (editingRewardIndex != null) {
        val indexToEdit = editingRewardIndex!!
        Dialog(onDismissRequest = { editingRewardIndex = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                border = BorderStroke(1.5.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "✏️ Sửa Phần Thưởng",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                    )
                    
                    Text(
                        text = "Thay đổi tên phần thưởng tự chọn của bạn:",
                        fontSize = 13.sp,
                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                    
                    OutlinedTextField(
                        value = editingRewardText,
                        onValueChange = { editingRewardText = it },
                        placeholder = { Text("Tên phần thưởng...", fontSize = 12.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF1E293B),
                            focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { editingRewardIndex = null }) {
                            Text("Hủy", color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                val cleaned = editingRewardText.trim()
                                if (cleaned.isNotEmpty()) {
                                    val updatedList = rewards.toMutableList().apply {
                                        set(indexToEdit, cleaned)
                                    }
                                    rewards = updatedList
                                    saveRewards(context, updatedList)
                                    editingRewardIndex = null
                                    Toast.makeText(context, "Đã cập nhật phần thưởng!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Tên phần thưởng không được để trống!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Text("Lưu", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Helpers for lucky wheel reward list serialization
fun loadRewards(context: Context): List<String> {
    val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
    val raw = prefs.getString("lucky_wheel_rewards_list", null)
    return if (!raw.isNullOrEmpty()) {
        raw.split("|||")
    } else {
        listOf(
            "Trà sữa 🧋",
            "Nghỉ 15 phút ☕",
            "Xem phim 🎬",
            "Chơi game 🎮",
            "Ăn pizza 🍕",
            "Đọc sách 📚",
            "Cà phê ☕",
            "Nghỉ ngơi 10p 🛋️"
        )
    }
}

fun saveRewards(context: Context, rewards: List<String>) {
    val prefs = context.getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("lucky_wheel_rewards_list", rewards.joinToString("|||")).apply()
}

// --- Screen 4: SyncProfileScreen ---
@Composable
fun SyncProfileScreen(
    viewModel: WorkoutViewModel,
    isLoggedIn: Boolean,
    userName: String,
    userEmail: String,
    userAvatar: String?,
    isSyncing: Boolean,
    lastSyncTime: Long,
    cloudItemsCount: Int,
    autoSync: Boolean,
    isDarkTheme: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    backgroundImagePath: String?,
    onBackgroundImageChanged: (String?) -> Unit
) {
    val context = LocalContext.current

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("824449193335-cq7fsd7f1b7hi6qlfj8k93dfjlaqk3mf.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val email = account?.email ?: ""
            val displayName = account?.displayName ?: ""
            if (idToken != null) {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                viewModel.signInWithGoogleCredential(credential)
            } else if (email.isNotEmpty()) {
                viewModel.signInOrSignUpWithEmail(email, "default123456", displayName.ifEmpty { "Người dùng Google" })
            } else {
                Toast.makeText(context, "Không lấy được thông tin email Google.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            val statusCode = (e as? ApiException)?.statusCode
            Toast.makeText(context, "Đăng nhập Google thất bại (Error Code: $statusCode): ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcomer Account Profile
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoggedIn) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userName.take(1).uppercase(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = userName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = userEmail,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Logout, contentDescription = "Sign out")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đăng Xuất")
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFFAF0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "None",
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(54.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Học Không Giới Hạn",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Đồng bộ đám mây và lưu trữ tiến độ ôn tập an toàn.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    try {
                                        launcher.launch(googleSignInClient.signInIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Mở trình đăng nhập Google thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Login, contentDescription = "Sign in")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đăng nhập tài khoản Google")
                        }
                    }
                }
            }
        }

        // Cloud sync status cell
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Đồng Bộ Đám Mây",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val syncSdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            val lastSyncStr = if (lastSyncTime > 0) syncSdf.format(Date(lastSyncTime)) else "Chưa đồng bộ"
                            Text("Lần đồng bộ cuối:", fontSize = 12.sp, color = Color.Gray)
                            Text(lastSyncStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Bản ghi đã lưu đám mây: $cloudItemsCount", fontSize = 11.sp, color = Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold)
                        }
                        
                        Button(
                            onClick = { viewModel.triggerSync() },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang đồng bộ...", fontSize = 12.sp)
                            } else {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đồng Bộ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tự động đồng bộ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Text("Đồng bộ tức thời khi lưu mốc ôn tập", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = autoSync,
                            onCheckedChange = { viewModel.setAutoSync(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4F46E5)
                            )
                        )
                    }
                }
            }
        }

        // Personalization Settings Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🎨 Giao Diện & Cá Nhân Hóa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                    )
                    
                    Divider(color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Chế độ nền tối", 
                                fontSize = 14.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                            )
                            Text(
                                text = "Chuyển giao diện sang tông màu tối thư thái", 
                                fontSize = 11.sp, 
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onDarkThemeChanged(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4F46E5)
                            )
                        )
                    }
                    
                    Divider(color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9))
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ảnh nền cá nhân", 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                        )
                        Text(
                            text = "Chọn hình ảnh làm nền mờ phía sau ứng dụng", 
                            fontSize = 11.sp, 
                            color = Color.Gray
                        )
                        
                        val imagePickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null) {
                                val path = saveChosenBackgroundImage(context, uri)
                                if (path != null) {
                                    onBackgroundImageChanged(path)
                                    Toast.makeText(context, "Đã cập nhật ảnh nền cá nhân thành công! ✨", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Lỗi khi sao chép ảnh nền.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image, 
                                    contentDescription = "Chọn ảnh nền", 
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chọn Ảnh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            if (backgroundImagePath != null) {
                                Button(
                                    onClick = { 
                                        onBackgroundImageChanged(null)
                                        Toast.makeText(context, "Đã xóa ảnh nền cá nhân.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete, 
                                        contentDescription = "Xóa ảnh nền", 
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Xóa Ảnh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
