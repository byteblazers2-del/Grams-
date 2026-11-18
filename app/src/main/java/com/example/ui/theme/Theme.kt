package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = VaultBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF173753),
    onPrimaryContainer = Color(0xFFD6EEFF),
    secondary = VaultTextSecondary,
    background = VaultDarkBackground,
    onBackground = VaultTextPrimary,
    surface = VaultDarkSurface,
    onSurface = VaultTextPrimary,
    surfaceVariant = VaultDarkCard,
    onSurfaceVariant = VaultTextSecondary,
    outline = VaultDivider,
    error = TonkeeperRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3A1822),
    onErrorContainer = Color(0xFFFFD9DE),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = VaultBluePressed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EDFF),
    onPrimaryContainer = Color(0xFF001E30),
    background = VaultLightBackground,
    onBackground = Color(0xFF10161F),
    surface = VaultLightSurface,
    onSurface = Color(0xFF10161F),
    surfaceVariant = VaultLightCard,
    onSurfaceVariant = Color(0xFF556270),
  )

private val VaultShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
  )

@Composable
fun GramVaultTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = VaultShapes,
    content = content,
  )
}
