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

// Material 3 tonal scheme generated from the green seed color #268C5C.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2B6A46),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB0F1C3),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4F6354),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D5),
    onSecondaryContainer = Color(0xFF0C1F13),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F8),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFF6FBF4),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF4),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC0C9C0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE5EAE3),
    surfaceContainerHighest = Color(0xFFDFE4DD)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D5A8),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF0F5130),
    onPrimaryContainer = Color(0xFFB0F1C3),
    secondary = Color(0xFFB6CCB9),
    onSecondary = Color(0xFF213527),
    secondaryContainer = Color(0xFF374B3D),
    onSecondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFFA3CDDB),
    onTertiary = Color(0xFF033540),
    tertiaryContainer = Color(0xFF214C57),
    onTertiaryContainer = Color(0xFFBFE9F8),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DD),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE4DD),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8A938A),
    outlineVariant = Color(0xFF414942),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303631)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
