package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class SemanticColors(
    val isDark: Boolean,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val neutralBadgeContainer: Color,
    val onNeutralBadge: Color,
    val cardBorder: Color,
    val subtleSurface: Color,
    val topBarContainer: Color,
    val onTopBar: Color,
    val generatorGlowStart: Color,
    val generatorGlowEnd: Color
)

val LocalSemanticColors = staticCompositionLocalOf { DarkSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = PureWhite,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo950,
    secondary = Emerald600,
    onSecondary = PureWhite,
    secondaryContainer = Emerald100,
    onSecondaryContainer = Emerald950,
    tertiary = Zinc700,
    onTertiary = PureWhite,
    background = Zinc50,
    onBackground = Zinc950,
    surface = PureWhite,
    onSurface = Zinc950,
    surfaceVariant = Zinc100,
    onSurfaceVariant = Zinc500,
    outline = Zinc300,
    outlineVariant = Zinc200,
    error = Crimson600,
    onError = PureWhite,
    errorContainer = Crimson100,
    onErrorContainer = Crimson950
)

private val DarkColorScheme = darkColorScheme(
    primary = Indigo500,
    onPrimary = PureWhite,
    primaryContainer = Indigo950,
    onPrimaryContainer = Indigo400,
    secondary = Emerald500,
    onSecondary = Zinc950,
    secondaryContainer = Emerald950,
    onSecondaryContainer = Emerald400,
    tertiary = Zinc300,
    onTertiary = Zinc950,
    background = Zinc950,
    onBackground = Zinc100,
    surface = Zinc900,
    onSurface = Zinc100,
    surfaceVariant = Zinc850,
    onSurfaceVariant = Zinc400,
    outline = Zinc700,
    outlineVariant = Zinc800,
    error = Crimson400,
    onError = Zinc950,
    errorContainer = Crimson950,
    onErrorContainer = Crimson100
)

private val LightSemanticColors = SemanticColors(
    isDark = false,
    success = Emerald600,
    onSuccess = PureWhite,
    successContainer = Emerald100,
    onSuccessContainer = Emerald950,
    warning = Amber600,
    warningContainer = Amber100,
    onWarningContainer = Amber950,
    neutralBadgeContainer = Zinc200,
    onNeutralBadge = Zinc600,
    cardBorder = Zinc200,
    subtleSurface = Zinc100,
    topBarContainer = PureWhite,
    onTopBar = Zinc950,
    generatorGlowStart = Indigo100.copy(alpha = 0.55f),
    generatorGlowEnd = Emerald100.copy(alpha = 0.45f)
)

private val DarkSemanticColors = SemanticColors(
    isDark = true,
    success = Emerald400,
    onSuccess = Zinc950,
    successContainer = Emerald950,
    onSuccessContainer = Emerald400,
    warning = Amber400,
    warningContainer = Amber950,
    onWarningContainer = Amber400,
    neutralBadgeContainer = Zinc800,
    onNeutralBadge = Zinc400,
    cardBorder = Zinc700.copy(alpha = 0.65f),
    subtleSurface = Zinc850,
    topBarContainer = Zinc900,
    onTopBar = Zinc100,
    generatorGlowStart = Indigo950.copy(alpha = 0.85f),
    generatorGlowEnd = Emerald950.copy(alpha = 0.55f)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun LicenseAdminTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
