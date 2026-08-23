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
import kotlin.random.Random

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    accentColor: Color = Color(0xFFC084FC),
    secondaryColor: Color = Color(0xFF818CF8),
    height: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizerAnim")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val beatScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beatScale"
    )

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
                val wave1 = Math.sin(phase.toDouble() + (normalizedIndex * 4.0)).toFloat()
                val wave2 = Math.cos((phase * 1.6).toDouble() + (normalizedIndex * 7.0)).toFloat()
                val centerWeight = 1f - Math.abs(normalizedIndex - 0.5f) * 1.2f
                val combined = ((wave1 * 0.5f + wave2 * 0.5f + 1f) / 2f) * beatScale * centerWeight
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
