package com.example.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.core.preferences.AppThemeMode

private val DarkColorScheme = darkColorScheme(
  primary = CobaltPrimaryDark,
  onPrimary = CobaltOnPrimaryDark,
  primaryContainer = CobaltContainerDark,
  onPrimaryContainer = CobaltOnContainerDark,
  secondary = SlateSecondaryDark,
  onSecondary = SlateOnSecondaryDark,
  secondaryContainer = SlateContainerDark,
  onSecondaryContainer = SlateOnContainerDark,
  tertiary = TealTertiaryDark,
  onTertiary = TealOnTertiaryDark,
  tertiaryContainer = TealContainerDark,
  onTertiaryContainer = TealOnContainerDark,
  background = BackgroundDark,
  onBackground = OnSurfaceDark,
  surface = SurfaceDark,
  onSurface = OnSurfaceDark,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = OnSurfaceVariantDark,
  outline = OutlineDark,
)

private val LightColorScheme = lightColorScheme(
  primary = CobaltPrimaryLight,
  onPrimary = CobaltOnPrimaryLight,
  primaryContainer = CobaltContainerLight,
  onPrimaryContainer = CobaltOnContainerLight,
  secondary = SlateSecondaryLight,
  onSecondary = SlateOnSecondaryLight,
  secondaryContainer = SlateContainerLight,
  onSecondaryContainer = SlateOnContainerLight,
  tertiary = TealTertiaryLight,
  onTertiary = TealOnTertiaryLight,
  tertiaryContainer = TealContainerLight,
  onTertiaryContainer = TealOnContainerLight,
  background = BackgroundLight,
  onBackground = OnSurfaceLight,
  surface = SurfaceLight,
  onSurface = OnSurfaceLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = OnSurfaceVariantLight,
  outline = OutlineLight,
)

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun BtucmTheme(
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
  }

  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      content = content,
    )
  }
}
