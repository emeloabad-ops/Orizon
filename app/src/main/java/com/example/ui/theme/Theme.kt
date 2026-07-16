package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BankGold,
    secondary = BankBlue,
    tertiary = BankJade,
    background = BankNavy,
    surface = BankObsidian,
    onPrimary = BankNavy,
    onSecondary = BankWhite,
    onBackground = BankWhite,
    onSurface = BankWhite,
    error = BankRed
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BankBlue,
    secondary = BankNavy,
    tertiary = BankJade,
    background = BankLightBg,
    surface = BankLightSurface,
    onPrimary = BankWhite,
    onSecondary = BankWhite,
    onBackground = BankDarkText,
    onSurface = BankDarkText,
    error = BankRed
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors to guarantee premium banking brand identity consistency
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
