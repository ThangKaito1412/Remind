package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.compose.runtime.*
import com.example.ui.FitMinderApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.WorkoutViewModel
import com.example.ui.viewmodel.WorkoutViewModelFactory

class MainActivity : ComponentActivity() {
  private val viewModel: WorkoutViewModel by viewModels {
    WorkoutViewModelFactory(application)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Request POST_NOTIFICATIONS runtime permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    setContent {
      val prefs = remember { getSharedPreferences("fitminder_prefs", Context.MODE_PRIVATE) }
      var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("is_dark_theme", false)) }
      var backgroundImagePath by remember { mutableStateOf(prefs.getString("custom_background_path", null)) }

      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          FitMinderApp(
            viewModel = viewModel,
            isDarkTheme = isDarkTheme,
            onDarkThemeChanged = { newValue ->
              isDarkTheme = newValue
              prefs.edit().putBoolean("is_dark_theme", newValue).apply()
            },
            backgroundImagePath = backgroundImagePath,
            onBackgroundImageChanged = { newValue ->
              backgroundImagePath = newValue
              if (newValue == null) {
                prefs.edit().remove("custom_background_path").apply()
              } else {
                prefs.edit().putString("custom_background_path", newValue).apply()
              }
            }
          )
        }
      }
    }
  }
}

