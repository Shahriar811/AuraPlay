package com.example.auraplay.widget

import com.example.auraplay.ui.MotionButton as Button

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
            var selected by rememberSaveable { mutableStateOf(WidgetStyles.get(this, id)) }
            var shape by rememberSaveable { mutableStateOf(WidgetStyles.shape(this, id)) }
            Surface(Modifier.fillMaxSize()) { Column(
                Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("AuraPlay widget", style = MaterialTheme.typography.headlineMedium)
                WidgetAppearanceOptions(selected, shape, onStyleChange = { selected = it }, onShapeChange = { shape = it })
                Button(onClick = {
                    WidgetStyles.setAppearance(this@WidgetConfigActivity, id, selected, shape)
                    AuraPlayAppWidgetProvider.updateAppWidget(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), id)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                }, modifier = Modifier.fillMaxWidth()) { Text("Save widget") }
            } }
        } }
    }
}
