package com.example.auraplay.widget

import com.example.auraplay.ui.MotionButton as Button

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.auraplay.ui.theme.AuraPlayTheme

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent { AuraPlayTheme {
            var selected by remember { mutableStateOf(WidgetStyles.get(this, id)) }
            Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("AuraPlay widget", style = MaterialTheme.typography.headlineMedium)
                WidgetStyle.entries.forEach { style ->
                    OutlinedCard(onClick = { selected = style }) { ListItem(headlineContent = { Text(style.title) }, supportingContent = { Text(style.description) }, trailingContent = { RadioButton(selected == style, { selected = style }) }) }
                }
                Button(onClick = {
                    WidgetStyles.set(this@WidgetConfigActivity, id, selected)
                    AuraPlayAppWidgetProvider.updateAppWidget(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), id)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                }, modifier = Modifier.fillMaxWidth()) { Text("Save widget") }
            } }
        } }
    }
}
