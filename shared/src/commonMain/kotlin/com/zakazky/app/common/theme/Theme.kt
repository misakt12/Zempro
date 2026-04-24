package com.zakazky.app.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Premium Color Palette
val Slate50 = Color(0xFFf8fafc)
val Slate100 = Color(0xFFf1f5f9)
val Slate200 = Color(0xFFe2e8f0)
val Slate300 = Color(0xFFcbd5e1)
val Slate400 = Color(0xFF94a3b8)
val Slate500 = Color(0xFF64748b)
val Slate600 = Color(0xFF475569)
val Slate800 = Color(0xFF1e293b)
val Slate900 = Color(0xFF0f172a)

val Blue50 = Color(0xFFeff6ff)
val Blue100 = Color(0xFFdbeafe)
val Blue200 = Color(0xFFbfdbfe)
val Blue500 = Color(0xFF3b82f6)
val Blue600 = Color(0xFF2563eb)
val Indigo50 = Color(0xFFeef2ff)
val Indigo600 = Color(0xFF4f46e5)
val Indigo700 = Color(0xFF4338ca)
val Indigo900 = Color(0xFF312e81)

val Navy600 = Color(0xFF283F6E)
val Navy700 = Color(0xFF1E2F52)
val Navy800 = Color(0xFF131D33)
val Navy900 = Color(0xFF0B1120)

val ZemproGreen = Color(0xFF10b981)

val SuccessLight = Color(0xFFdcfce7)
val SuccessDark = Color(0xFF15803d)
val WarningLight = Color(0xFFfef9c3)
val WarningDark = Color(0xFFa16207)

val ErrorLight = Color(0xFFfef2f2)
val ErrorDark = Color(0xFFb91c1c)

val PrimaryGradientStart = Indigo600
val PrimaryGradientEnd = Blue500

val Blue400 = Color(0xFF60a5fa)
val Blue900 = Color(0xFF1e3a8a)  // Hluboká modrá pro status bannery

private val PremiumDarkBluePalette = darkColors(
    primary = Blue500,
    primaryVariant = Indigo600,
    secondary = Blue400,
    background = Navy900,
    surface = Navy800,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Slate50,
    onSurface = Slate50,
)

val AppTypography = Typography(
    h1 = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-1).sp,
        color = Color.White
    ),
    h6 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),
    subtitle1 = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
        color = Slate50
    ),
    body1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        color = Slate100
    ),
    body2 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp,
        color = Slate200
    ),
    caption = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        color = Slate300
    )
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(32.dp)
)

@Composable
fun ZakazkyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = PremiumDarkBluePalette, // Enforcing deep blue premium theme
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
