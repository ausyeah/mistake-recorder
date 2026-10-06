package com.mistakebook.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 经典浅色暖纸模式（Warm Paper）
private val PaperLightColors = lightColorScheme(
    primary = PaperPrimary,
    onPrimary = Color.White,
    primaryContainer = PaperPrimaryTint,
    onPrimaryContainer = PaperPrimaryDark,
    secondary = PaperTextSecondaryLight,
    onSecondary = Color.White,
    background = PaperBgLight,
    onBackground = PaperTextPrimaryLight,
    surface = PaperCardLight,
    onSurface = PaperTextPrimaryLight,
    surfaceVariant = Color(0xFFEFECE3),
    onSurfaceVariant = PaperTextMutedLight,
    outline = PaperBorderLight,
    error = PaperBad,
    onError = Color.White
)

// 深色墨砚夜读模式（Dark Ink Paper）
private val PaperDarkColors = darkColorScheme(
    primary = PaperPrimaryDarkTheme,
    onPrimary = Color(0xFF141930),
    primaryContainer = PaperPrimaryTintDark,
    onPrimaryContainer = Color(0xFFD6DEFC),
    secondary = PaperTextSecondaryDark,
    onSecondary = Color.White,
    background = PaperBgDark,
    onBackground = PaperTextPrimaryDark,
    surface = PaperCardDark,
    onSurface = PaperTextPrimaryDark,
    surfaceVariant = Color(0xFF2C2B35),
    onSurfaceVariant = PaperTextMutedDark,
    outline = PaperBorderDark,
    error = PaperBadDark,
    onError = Color.White
)

private val MistakeBookTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.02.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.02.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

/**
 * 全局纸质化主题系统（支持浅色暖纸与深色墨砚自适应）。
 */
@Composable
fun MistakeBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val activity = LocalContextActivity()
    val view = LocalView.current
    val colorScheme = if (darkTheme) PaperDarkColors else PaperLightColors

    if (!view.isInEditMode) {
        SideEffect {
            val window = activity?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MistakeBookTypography,
        content = content
    )
}

@Composable
private fun LocalContextActivity(): Activity? {
    var context: android.content.Context = LocalContext.current
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
