package com.example.auraplay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val MenuShape = RoundedCornerShape(24.dp)
private val MenuItemShape = RoundedCornerShape(14.dp)
private val DialogShape = RoundedCornerShape(28.dp)

@Composable
internal fun AuraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 224.dp, max = 304.dp).testTag("aura-popup-menu"),
        shape = MenuShape,
        containerColor = colors.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.65f)),
        content = content
    )
}

@Composable
internal fun AuraMenuItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector,
    enabled: Boolean = true,
    selected: Boolean? = null,
    destructive: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val foreground = when {
        destructive -> colors.error
        selected == true -> colors.primary
        else -> colors.onSurface
    }
    DropdownMenuItem(
        text = {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        trailingIcon = if (selected == true) { { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) } } else null,
        modifier = Modifier.padding(horizontal = 6.dp).clip(MenuItemShape)
            .background(if (selected == true) colors.primaryContainer else Color.Transparent)
            .semantics { if (selected != null) this.selected = selected },
        colors = MenuDefaults.itemColors(textColor = foreground,
            leadingIconColor = if (destructive) colors.error else colors.primary,
            trailingIconColor = colors.primary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
internal fun AuraMenuDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
}

@Composable
internal fun AuraAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        title = title?.let { { ProvideTextStyle(MaterialTheme.typography.titleLarge) { it() } } },
        text = text?.let { { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() } } },
        modifier = modifier.border(1.dp, colors.outlineVariant.copy(alpha = 0.65f), DialogShape)
            .testTag("aura-dialog"),
        shape = DialogShape,
        containerColor = colors.surfaceContainerLow,
        tonalElevation = 0.dp,
        iconContentColor = colors.primary,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant
    )
}
