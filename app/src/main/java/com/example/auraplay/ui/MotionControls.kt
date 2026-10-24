package com.example.auraplay.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/** Draw-only feedback preserves touch targets and never delays the actual action. */
@Composable
internal fun Modifier.pressFeedback(
    source: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.94f
): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(source, enabled, pressedScale) {
        if (!enabled) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        source.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(pressedScale, tween(80))
                is PressInteraction.Release -> {
                    // A quick tap can begin and end within one frame. Still show the press.
                    if (scale.value > pressedScale + 0.005f) scale.animateTo(pressedScale, tween(60))
                    scale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 600f))
                }
                is PressInteraction.Cancel -> scale.animateTo(1f, spring(stiffness = 700f))
            }
        }
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

@Composable
fun MotionIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    IconButton(onClick, modifier.pressFeedback(source, enabled, 0.88f), enabled, colors, source, content)
}

@Composable
fun MotionFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    FilledTonalIconButton(onClick, modifier.pressFeedback(source, enabled, 0.88f), enabled,
        shape, colors, interactionSource = source, content = content)
}

@Composable
fun MotionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Button(onClick, modifier.pressFeedback(source, enabled), enabled, shape, colors, elevation,
        border, contentPadding, source, content)
}

@Composable
fun MotionFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    FilledTonalButton(onClick, modifier.pressFeedback(source, enabled), enabled, shape, colors,
        contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
fun MotionOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    OutlinedButton(onClick, modifier.pressFeedback(source, enabled), enabled, shape, colors,
        border = border, contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
fun MotionTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    TextButton(onClick, modifier.pressFeedback(source, enabled), enabled, colors = colors,
        contentPadding = contentPadding, interactionSource = source, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = FilterChipDefaults.shape
) {
    val source = remember { MutableInteractionSource() }
    FilterChip(selected, onClick, label, modifier.pressFeedback(source, enabled), enabled,
        shape = shape, interactionSource = source)
}

@Composable
fun MotionFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    FloatingActionButton(onClick, modifier.pressFeedback(source), shape, containerColor, contentColor,
        interactionSource = source, content = content)
}
