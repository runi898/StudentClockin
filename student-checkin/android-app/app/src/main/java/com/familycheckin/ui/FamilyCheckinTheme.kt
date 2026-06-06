package com.familycheckin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FamilyPalette {
    val Canvas = Color(0xFFF9F6EF)
    val CanvasSoft = Color(0xFFF3F7F1)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF6F0E4)
    val SurfaceAccent = Color(0xFFE5F0E7)
    val Ink = Color(0xFF162019)
    val InkSoft = Color(0xFF667066)
    val Accent = Color(0xFF2D7258)
    val AccentStrong = Color(0xFF1E533F)
    val AccentWarm = Color(0xFFF4DCC8)
    val Success = Color(0xFFD6EAD8)
    val Line = Color(0xFFE9E1D7)
}

private val familyColorScheme: ColorScheme = lightColorScheme(
    primary = FamilyPalette.Accent,
    onPrimary = Color.White,
    secondary = FamilyPalette.AccentWarm,
    onSecondary = FamilyPalette.Ink,
    background = FamilyPalette.Canvas,
    onBackground = FamilyPalette.Ink,
    surface = FamilyPalette.Surface,
    onSurface = FamilyPalette.Ink,
    surfaceVariant = FamilyPalette.SurfaceMuted,
    onSurfaceVariant = FamilyPalette.InkSoft,
    outline = FamilyPalette.Line
)

private val familyTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 34.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )
)

private val familyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun FamilyCheckinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = familyColorScheme,
        typography = familyTypography,
        shapes = familyShapes,
        content = content
    )
}

@Composable
fun ScreenBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(FamilyPalette.Canvas, FamilyPalette.CanvasSoft)
            )
        ),
        content = content
    )
}
