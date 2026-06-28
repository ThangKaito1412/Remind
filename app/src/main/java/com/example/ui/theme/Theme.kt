package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD3E2FF),
    onPrimary = Color(0xFF001D36),
    primaryContainer = Color(0xFF005FB0),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFE8DEF8),
    onSecondary = Color(0xFF21005D),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFF3F4F9),
    surface = Color(0xFF2A2B2F),
    onSurface = Color(0xFFF3F4F9),
    surfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFC1C7CE)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF005FB0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E2FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFFE8DEF8),
    onSecondary = Color(0xFF21005D),
    secondaryContainer = Color(0xFFFDE7E9),
    onSecondaryContainer = Color(0xFF410002),
    background = Color(0xFFF3F4F9),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFC1C7CE)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic colors by default so our theme colors apply perfectly
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

