package com.example.auraplay.features

import com.example.auraplay.EqualizerState
import org.json.JSONArray
import org.json.JSONObject

fun EqualizerState.encode(): String = JSONObject().put("enabled", isEnabled).put("bands", JSONArray(bandLevels))
    .put("bass", bassBoost).put("virtualizer", virtualizer).put("reverb", reverbPreset).toString()

fun decodeEqualizer(json: String): EqualizerState = try {
    val j = JSONObject(json)
    val bands = j.optJSONArray("bands")
    EqualizerState(j.optBoolean("enabled"), bands?.let { (0 until minOf(it.length(), 32)).map { i -> it.optInt(i).coerceIn(-1500, 1500) } } ?: List(5) { 0 },
        j.optInt("bass").coerceIn(0, 1000), j.optInt("virtualizer").coerceIn(0, 1000), j.optInt("reverb").coerceIn(0, 6))
} catch (_: Exception) { EqualizerState() }
