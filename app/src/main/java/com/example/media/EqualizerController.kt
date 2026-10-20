package com.example.media

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBandSpec(
    val bandIndex: Int,
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val currentLevelMb: Int
)

data class HardwareEqualizerState(
    val isSupportedOnDevice: Boolean = false,
    val isBassBoostSupported: Boolean = false,
    val activeAudioSessionId: Int = 0,
    val isEnabled: Boolean = false,
    val presets: List<String> = listOf("Flat", "Bass & Warmth", "Vocal Clarity", "Acoustic Studio", "Cinema Wide"),
    val selectedPresetIndex: Int = 0,
    val bands: List<EqualizerBandSpec> = emptyList(),
    val bassBoostStrength: Int = 0, // 0..1000
    val statusMessage: String = "Waiting for active audio session"
)

/**
 * Phase 10: Real Android AudioEffect Equalizer & BassBoost controller with device capability
 * detection, graceful fallback when unsupported or when audioSessionId == 0, and clean release.
 */
class EqualizerController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var boundSessionId: Int = 0

    private val _state = MutableStateFlow(HardwareEqualizerState())
    val state: StateFlow<HardwareEqualizerState> = _state.asStateFlow()

    private val descriptorSupported: Boolean by lazy {
        runCatching {
            val descriptors = AudioEffect.queryEffects() ?: emptyArray()
            descriptors.any { it.type == AudioEffect.EFFECT_TYPE_EQUALIZER }
        }.getOrDefault(false)
    }

    fun bindToAudioSession(
        audioSessionId: Int,
        enabled: Boolean,
        presetIndex: Int,
        bassStrength: Int,
        savedBandLevelsMb: List<Int>
    ) {
        if (audioSessionId <= 0) {
            _state.value = _state.value.copy(
                isSupportedOnDevice = descriptorSupported,
                activeAudioSessionId = 0,
                isEnabled = enabled,
                selectedPresetIndex = presetIndex,
                bassBoostStrength = bassStrength,
                bands = buildFallbackOrSavedBands(savedBandLevelsMb),
                statusMessage = if (descriptorSupported) {
                    "Start playback to bind hardware DSP to active audio session"
                } else {
                    "Hardware Equalizer DSP not reported on this device/emulator; preferences saved safely"
                }
            )
            return
        }

        if (boundSessionId != audioSessionId) {
            releaseEffects()
            boundSessionId = audioSessionId
        }

        try {
            if (equalizer == null) {
                equalizer = Equalizer(0, audioSessionId)
            }
            val eq = equalizer!!
            eq.enabled = enabled

            val numBands = eq.numberOfBands.toInt().coerceAtLeast(1)
            val range = eq.bandLevelRange
            val minMb = range.getOrElse(0) { (-1500).toShort() }.toInt()
            val maxMb = range.getOrElse(1) { 1500.toShort() }.toInt()

            val devicePresets = mutableListOf<String>()
            val numPresets = eq.numberOfPresets.toInt()
            for (p in 0 until numPresets) {
                runCatching { devicePresets.add(eq.getPresetName(p.toShort())) }
            }
            if (devicePresets.isEmpty()) {
                devicePresets.addAll(listOf("Flat", "Bass & Warmth", "Vocal Clarity", "Acoustic Studio", "Cinema Wide"))
            }

            val bandSpecs = mutableListOf<EqualizerBandSpec>()
            for (b in 0 until numBands) {
                val targetLevel = savedBandLevelsMb.getOrElse(b) { 0 }.coerceIn(minMb, maxMb)
                runCatching { eq.setBandLevel(b.toShort(), targetLevel.toShort()) }
                val centerFreqMilliHz = runCatching { eq.getCenterFreq(b.toShort()) }.getOrDefault(1000_000)
                val currentMb = runCatching { eq.getBandLevel(b.toShort()).toInt() }.getOrDefault(targetLevel)
                bandSpecs.add(
                    EqualizerBandSpec(
                        bandIndex = b,
                        centerFreqHz = (centerFreqMilliHz / 1000).coerceAtLeast(30),
                        minLevelMb = minMb,
                        maxLevelMb = maxMb,
                        currentLevelMb = currentMb
                    )
                )
            }

            // BassBoost capability check
            var bassSupported = false
            runCatching {
                if (bassBoost == null) {
                    bassBoost = BassBoost(0, audioSessionId)
                }
                val bb = bassBoost!!
                bassSupported = bb.strengthSupported
                bb.enabled = enabled && bassSupported
                if (bassSupported) {
                    bb.setStrength(bassStrength.coerceIn(0, 1000).toShort())
                }
            }

            _state.value = HardwareEqualizerState(
                isSupportedOnDevice = true,
                isBassBoostSupported = bassSupported,
                activeAudioSessionId = audioSessionId,
                isEnabled = enabled,
                presets = devicePresets,
                selectedPresetIndex = presetIndex.coerceIn(0, (devicePresets.size - 1).coerceAtLeast(0)),
                bands = bandSpecs,
                bassBoostStrength = bassStrength.coerceIn(0, 1000),
                statusMessage = "Bound to hardware AudioSession #$audioSessionId (${bandSpecs.size} bands)"
            )
        } catch (e: Exception) {
            releaseEffects()
            _state.value = HardwareEqualizerState(
                isSupportedOnDevice = false,
                isBassBoostSupported = false,
                activeAudioSessionId = audioSessionId,
                isEnabled = false,
                selectedPresetIndex = presetIndex,
                bands = buildFallbackOrSavedBands(savedBandLevelsMb),
                bassBoostStrength = bassStrength,
                statusMessage = "Device audio session #$audioSessionId rejected hardware EQ (${e.javaClass.simpleName}); graceful fallback active"
            )
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int): List<Int> {
        val currentBands = _state.value.bands.toMutableList()
        if (bandIndex in currentBands.indices) {
            val spec = currentBands[bandIndex]
            val clamped = levelMb.coerceIn(spec.minLevelMb, spec.maxLevelMb)
            runCatching {
                equalizer?.setBandLevel(bandIndex.toShort(), clamped.toShort())
            }
            currentBands[bandIndex] = spec.copy(currentLevelMb = clamped)
            _state.value = _state.value.copy(bands = currentBands)
        }
        return currentBands.map { it.currentLevelMb }
    }

    fun applyPreset(presetIndex: Int): List<Int> {
        val eq = equalizer
        val currentBands = _state.value.bands.toMutableList()
        if (eq != null && presetIndex in 0 until eq.numberOfPresets) {
            runCatching {
                eq.usePreset(presetIndex.toShort())
                for (i in currentBands.indices) {
                    val lvl = eq.getBandLevel(i.toShort()).toInt()
                    currentBands[i] = currentBands[i].copy(currentLevelMb = lvl)
                }
            }
        } else {
            // Built-in studio curve presets when device reports 0 native presets
            val curve = when (presetIndex) {
                1 -> listOf(600, 400, 0, 200, 300) // Bass & Warmth
                2 -> listOf(-200, 100, 500, 400, 100) // Vocal Clarity
                3 -> listOf(300, 150, -100, 250, 450) // Acoustic Studio
                4 -> listOf(500, 200, -150, 350, 600) // Cinema Wide
                else -> listOf(0, 0, 0, 0, 0) // Flat
            }
            for (i in currentBands.indices) {
                val lvl = curve.getOrElse(i) { 0 }.coerceIn(currentBands[i].minLevelMb, currentBands[i].maxLevelMb)
                runCatching { eq?.setBandLevel(i.toShort(), lvl.toShort()) }
                currentBands[i] = currentBands[i].copy(currentLevelMb = lvl)
            }
        }
        _state.value = _state.value.copy(
            selectedPresetIndex = presetIndex,
            bands = currentBands
        )
        return currentBands.map { it.currentLevelMb }
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            val bb = bassBoost
            if (bb != null && bb.strengthSupported) {
                bb.setStrength(clamped.toShort())
            }
        }
        _state.value = _state.value.copy(bassBoostStrength = clamped)
    }

    fun setEnabled(enabled: Boolean) {
        runCatching {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled && (_state.value.isBassBoostSupported)
        }
        _state.value = _state.value.copy(isEnabled = enabled)
    }

    private fun buildFallbackOrSavedBands(saved: List<Int>): List<EqualizerBandSpec> {
        val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
        return defaultFreqs.mapIndexed { idx, freq ->
            EqualizerBandSpec(
                bandIndex = idx,
                centerFreqHz = freq,
                minLevelMb = -1500,
                maxLevelMb = 1500,
                currentLevelMb = saved.getOrElse(idx) { 0 }.coerceIn(-1500, 1500)
            )
        }
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        boundSessionId = 0
    }
}
