package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val AlphaDarkColorScheme = darkColorScheme(
    primary = AlphaCyan,
    onPrimary = AlphaOnCyan,
    primaryContainer = AlphaCyanContainer,
    onPrimaryContainer = AlphaCyan,
    secondary = AlphaEmerald,
    onSecondary = AlphaOnCyan,
    secondaryContainer = AlphaEmeraldContainer,
    onSecondaryContainer = AlphaEmerald,
    tertiary = AlphaAmber,
    onTertiary = AlphaOnCyan,
    tertiaryContainer = AlphaAmberContainer,
    onTertiaryContainer = AlphaAmber,
    error = AlphaCrimson,
    onError = AlphaTextPrimary,
    errorContainer = AlphaCrimsonContainer,
    onErrorContainer = AlphaCrimson,
    background = AlphaObsidianBg,
    onBackground = AlphaTextPrimary,
    surface = AlphaSurfaceDark,
    onSurface = AlphaTextPrimary,
    surfaceVariant = AlphaSurfaceElevated,
    onSurfaceVariant = AlphaTextSecondary,
    surfaceContainerLowest = AlphaObsidianBg,
    surfaceContainerLow = AlphaSurfaceDark,
    surfaceContainer = AlphaSurfaceElevated,
    surfaceContainerHigh = AlphaSurfaceHighlight,
    outline = AlphaOutline,
    outlineVariant = AlphaOutline.copy(alpha = 0.5f)
)

val AlphaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AlphaMicTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        AlphaDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AlphaTypography,
        shapes = AlphaShapes,
        content = content
    )
}
