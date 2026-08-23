package com.example.auraplay.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ✨ Premium AuraPlay Typography — refined but simple
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,  // Cleaner and more modern than Default
        fontWeight = FontWeight.Medium,     // Slightly bolder for premium readability
        fontSize = 16.sp,                   // Balanced body text size
        lineHeight = 24.sp,                 // Comfortable for reading
        letterSpacing = 0.15.sp             // Slightly tighter spacing feels more elegant
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,   // Adds emphasis and hierarchy
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp           // Subtle compression = premium look
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp              // Slightly reduced spacing for precision
    )
)
