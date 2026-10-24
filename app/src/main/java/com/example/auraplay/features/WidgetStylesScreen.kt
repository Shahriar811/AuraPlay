package com.example.auraplay.features

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.auraplay.widget.*

@Composable
fun WidgetStylesScreen(nav: NavController) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(WidgetStyles.default(context)) }
    FeaturePage("Widget styles", nav) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Choose the style for your home screen widgets. Hold a widget on your launcher to resize or reconfigure it.")
            WidgetStyle.entries.forEach { style ->
                Card(onClick = { selected = style; WidgetStyles.setDefault(context, style) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when(style) { WidgetStyle.COMPACT -> Icons.Rounded.ViewAgenda; WidgetStyle.STANDARD -> Icons.Rounded.Widgets; WidgetStyle.ARTWORK -> Icons.Rounded.Image }, null)
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(style.title, fontWeight = FontWeight.Bold); Text(style.description, style = MaterialTheme.typography.bodySmall) }
                        RadioButton(selected == style, { selected = style; WidgetStyles.setDefault(context, style) })
                    }
                }
            }
            Text("Small widget sizes use the compact layout so the controls stay visible.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
