package com.example.auraplay

import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.media.audiofx.PresetReverb
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerState(
    val isEnabled: Boolean = false,
    val bandLevels: List<Int> = List(5) { 0 }, // 5 frequency bands
    val bassBoost: Int = 0, // 0-1000 (0.1mB steps)
    val virtualizer: Int = 0, // 0-1000
    val reverbPreset: Int = PresetReverb.PRESET_NONE.toInt()
)

class EqualizerManager(private val audioSessionId: Int) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState = _equalizerState.asStateFlow()

    init {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = false
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = false
            }
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = false
            }
            reverb = PresetReverb(0, audioSessionId).apply {
                enabled = false
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error initializing audio effects", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            if (!enabled) {
                // Reset all effects to neutral before disabling to avoid audio artifacts
                val numBands = getNumberOfBands()
                for (i in 0 until numBands) {
                    equalizer?.setBandLevel(i.toShort(), 0)
                }
                bassBoost?.setStrength(0)
                virtualizer?.setStrength(0)
                reverb?.setPreset(PresetReverb.PRESET_NONE)
            }
            
            // Disable effects first, then enable if needed
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled && (_equalizerState.value.bassBoost > 0)
            virtualizer?.enabled = enabled && (_equalizerState.value.virtualizer > 0)
            reverb?.enabled = enabled && (_equalizerState.value.reverbPreset != PresetReverb.PRESET_NONE.toInt())
            
            _equalizerState.value = _equalizerState.value.copy(isEnabled = enabled)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting enabled state", e)
        }
    }

    fun setBandLevel(band: Int, level: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), level.toShort())
            val newLevels = _equalizerState.value.bandLevels.toMutableList()
            if (band < newLevels.size) {
                newLevels[band] = level
                _equalizerState.value = _equalizerState.value.copy(bandLevels = newLevels)
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting band level", e)
        }
    }

    fun setBassBoost(strength: Int) {
        try {
            bassBoost?.setEnabled(strength > 0)
            if (strength > 0) {
                bassBoost?.setStrength(strength.toShort())
            }
            _equalizerState.value = _equalizerState.value.copy(bassBoost = strength)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting bass boost", e)
        }
    }

    fun setVirtualizer(strength: Int) {
        try {
            virtualizer?.setEnabled(strength > 0)
            if (strength > 0) {
                virtualizer?.setStrength(strength.toShort())
            }
            _equalizerState.value = _equalizerState.value.copy(virtualizer = strength)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting virtualizer", e)
        }
    }

    fun setReverbPreset(preset: Int) {
        try {
            val presetShort = preset.toShort()
            reverb?.setEnabled(presetShort != PresetReverb.PRESET_NONE)
            if (presetShort != PresetReverb.PRESET_NONE) {
                reverb?.setPreset(presetShort)
            }
            _equalizerState.value = _equalizerState.value.copy(reverbPreset = preset)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting reverb preset", e)
        }
    }

    fun getNumberOfBands(): Int {
        return equalizer?.numberOfBands?.toInt() ?: 5
    }

    fun getBandFreqRange(band: Int): IntArray? {
        return equalizer?.getBandFreqRange(band.toShort())
    }

    fun getCenterFreq(band: Int): Int {
        return try {
            equalizer?.getCenterFreq(band.toShort())?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getBandLevelRange(): IntArray? {
        return equalizer?.bandLevelRange?.map { it.toInt() }?.toIntArray()
    }

    fun reset() {
        try {
            val numBands = getNumberOfBands()
            for (i in 0 until numBands) {
                setBandLevel(i, 0)
            }
            setBassBoost(0)
            setVirtualizer(0)
            setReverbPreset(PresetReverb.PRESET_NONE.toInt())
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error resetting equalizer", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            reverb?.release()
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error releasing effects", e)
        }
    }
}

