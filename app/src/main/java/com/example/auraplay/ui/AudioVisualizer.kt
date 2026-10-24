package com.example.auraplay.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    accentColor: Color = Color(0xFFC4E6BB),
    secondaryColor: Color = Color(0xFFA9CDA4),
    height: Dp = 48.dp
) {
    // Start the animation only while playing and read its state during drawing.
    // Pausing no longer keeps an infinite animation or composition work alive.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) phase.animateTo((2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart))
        else phase.snapTo(0f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barWidth = (totalWidth / (barCount * 1.5f)).coerceAtLeast(3f)
        val spacing = (totalWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)

        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / barCount
            
            val barHeight = if (isPlaying) {
                val wave1 = Math.sin(phase.value.toDouble() + (normalizedIndex * 4.0)).toFloat()
                val wave2 = Math.cos((phase.value * 1.6).toDouble() + (normalizedIndex * 7.0)).toFloat()
                val centerWeight = 1f - Math.abs(normalizedIndex - 0.5f) * 1.2f
                val combined = ((wave1 * 0.5f + wave2 * 0.5f + 1f) / 2f) * centerWeight
                (combined.coerceIn(0.08f, 1.0f) * canvasHeight)
            } else {
                canvasHeight * 0.08f
            }

            val startX = i * (barWidth + spacing)
            val startY = (canvasHeight - barHeight) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor,
                        secondaryColor
                    ),
                    startY = startY,
                    endY = startY + barHeight
                ),
                topLeft = Offset(startX, startY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
