package com.omniutility.core.ui.theme

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

private val OrixColorScheme = darkColorScheme(
    primary = Color(0xFFF0C38E),
    onPrimary = Color(0xFF312C51),
    secondary = Color(0xFFF1AA9B),
    onSecondary = Color(0xFF312C51),
    background = Color(0xFF48426D),
    surface = Color(0xFF312C51),
    surfaceVariant = Color(0xFF312C51),
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = Color(0xFFF0C38E),
    onPrimaryContainer = Color(0xFF312C51),
    onSurfaceVariant = Color(0xFFD0CDD8)
)

@Composable
fun OmniUtilityTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Force Orix Brand
  content: @Composable () -> Unit,
) {
  val colorScheme = OrixColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
