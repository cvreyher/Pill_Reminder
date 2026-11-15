package com.vonreyher.medicationtracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Material 3 tonal scheme generated from the brand purple #6E5A83.
private val LightColors = lightColorScheme(
    primary = Color(0xFF6B5585),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDD9FF),
    onPrimaryContainer = Color(0xFF261136),
    secondary = Color(0xFF665A6F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDDDF5),
    onSecondaryContainer = Color(0xFF211829),
    tertiary = Color(0xFF805157),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DD),
    onTertiaryContainer = Color(0xFF321017),
    background = Color(0xFFFFF7FD),
    onBackground = Color(0xFF1E1A20),
    surface = Color(0xFFFFF7FD),
    onSurface = Color(0xFF1E1A20),
    surfaceVariant = Color(0xFFE9DFEB),
    onSurfaceVariant = Color(0xFF4B454D),
    outline = Color(0xFF7C757E),
    outlineVariant = Color(0xFFCDC4CE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF1FA),
    surfaceContainer = Color(0xFFF4EBF4),
    surfaceContainerHigh = Color(0xFFEEE6EE),
    surfaceContainerHighest = Color(0xFFE8E0E8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD8BDF2),
    onPrimary = Color(0xFF3C2754),
    primaryContainer = Color(0xFF533D6C),
    onPrimaryContainer = Color(0xFFEDD9FF),
    secondary = Color(0xFFD0C1D9),
    onSecondary = Color(0xFF362C3F),
    secondaryContainer = Color(0xFF4D4357),
    onSecondaryContainer = Color(0xFFEDDDF5),
    tertiary = Color(0xFFF3B7BE),
    onTertiary = Color(0xFF4B252A),
    tertiaryContainer = Color(0xFF653A40),
    onTertiaryContainer = Color(0xFFFFD9DD),
    background = Color(0xFF161217),
    onBackground = Color(0xFFE9E0E8),
    surface = Color(0xFF161217),
    onSurface = Color(0xFFE9E0E8),
    surfaceVariant = Color(0xFF4B454D),
    onSurfaceVariant = Color(0xFFCDC4CE),
    outline = Color(0xFF968E98),
    outlineVariant = Color(0xFF4B454D),
    surfaceContainerLowest = Color(0xFF110D12),
    surfaceContainerLow = Color(0xFF1E1A20),
    surfaceContainer = Color(0xFF231E24),
    surfaceContainerHigh = Color(0xFF2D282E),
    surfaceContainerHighest = Color(0xFF383339)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off: the app always uses its own purple instead of the wallpaper colors.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Material 3 Expressive: springy motion, expressive shapes and emphasized type.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
