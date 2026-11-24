package com.example.auraplay.features

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.auraplay.R
import com.example.auraplay.ui.MotionButton
import com.example.auraplay.widget.WidgetAppearanceOptions
import com.example.auraplay.widget.WidgetStyles

@Composable
fun WidgetStylesScreen(nav: NavController) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(WidgetStyles.default(context)) }
    var shape by remember { mutableStateOf(WidgetStyles.defaultShape(context)) }
    var showLauncherInstructions by rememberSaveable { mutableStateOf(false) }
    FeaturePage("Widget styles", nav) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MotionButton(
                onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = WidgetStyles.provider(context, selected)
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
            WidgetAppearanceOptions(selected, shape,
                onStyleChange = { selected = it; WidgetStyles.setDefault(context, it) },
                onShapeChange = { shape = it; WidgetStyles.setDefaultShape(context, it) })
        }
    }
}
