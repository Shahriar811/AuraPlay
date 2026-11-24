package com.example.auraplay.features

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.auraplay.R
import com.example.auraplay.ui.MotionButton
import com.example.auraplay.widget.AuraPlayAppWidgetProvider
import com.example.auraplay.widget.WidgetStyle
import com.example.auraplay.widget.WidgetStyles

@Composable
fun WidgetStylesScreen(nav: NavController) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(WidgetStyles.default(context)) }
    var showLauncherInstructions by rememberSaveable { mutableStateOf(false) }
    FeaturePage("Widget styles", nav) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MotionButton(
                onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = ComponentName(context, AuraPlayAppWidgetProvider::class.java)
                    val pinRequested = try {
                        manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(provider, null, null)
                    } catch (_: IllegalStateException) {
                        false
                    }
                    showLauncherInstructions = !pinRequested
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Widgets, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.widget_add_to_home))
            }
            if (showLauncherInstructions) {
                Text(stringResource(R.string.widget_add_from_launcher), style = MaterialTheme.typography.bodyMedium)
            }
            WidgetStyle.entries.forEach { style ->
                Card(onClick = { selected = style; WidgetStyles.setDefault(context, style) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when(style) { WidgetStyle.COMPACT -> Icons.Rounded.ViewAgenda; WidgetStyle.STANDARD -> Icons.Rounded.Widgets; WidgetStyle.ARTWORK -> Icons.Rounded.Image }, null)
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(style.title, fontWeight = FontWeight.Bold); Text(style.description, style = MaterialTheme.typography.bodySmall) }
                        RadioButton(selected == style, { selected = style; WidgetStyles.setDefault(context, style) })
                    }
                }
            }
            Text("Hold the widget on your home screen to resize or reconfigure it. Smaller sizes use the Compact layout.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
