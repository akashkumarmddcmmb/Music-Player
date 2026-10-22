package com.example.media

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
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

enum class EqualizerProcessingMode(val label: String) {
    SOFTWARE_BIQUAD_DSP("Software Biquad DSP"),
    HARDWARE_AUDIO_EFFECT("Hardware AudioEffect"),
    BYPASSED("Bypassed")
}

enum class EqualizerDiagnosticState(val displayText: String) {
    EQUALIZER_DISABLED("Equalizer disabled"),
    WAITING_FOR_PLAYBACK("Waiting for playback"),
    SOFTWARE_PROCESSING_ACTIVE("Equalizer active • Software processing active"),
    HARDWARE_EFFECT_ACTIVE("Equalizer active • Hardware effect active"),
    HARDWARE_UNSUPPORTED_SOFTWARE_ACTIVE("Hardware effect unsupported; software processing active"),
    INITIALIZATION_FAILED("Processing initialization failed")
}

data class HardwareEqualizerState(
    val isSupportedOnDevice: Boolean = true,
    val isHardwareEffectSupportedOnDevice: Boolean = false,
    val isBassBoostSupported: Boolean = true,
    val activeAudioSessionId: Int = 0,
    val isPlaybackActive: Boolean = false,
    val isEnabled: Boolean = false,
    val processingMode: EqualizerProcessingMode = EqualizerProcessingMode.BYPASSED,
    val diagnosticState: EqualizerDiagnosticState = EqualizerDiagnosticState.EQUALIZER_DISABLED,
    val presets: List<String> = DEFAULT_PRESET_NAMES,
    val selectedPresetIndex: Int = 0,
    val bands: List<EqualizerBandSpec> = defaultBandSpecs(),
    val bassBoostStrength: Int = 0, // 0..1000
    val stereoBalance: Float = 0.0f, // -1.0f..1.0f
    val preampGainDb: Float = 0.0f,  // -6.0f..6.0f
    val statusMessage: String = EqualizerDiagnosticState.EQUALIZER_DISABLED.displayText
) {
    companion object {
        val DEFAULT_PRESET_NAMES = listOf(
            "Flat",
            "Bass & Warmth",
            "Vocal Clarity",
            "Acoustic Studio",
            "Cinema Wide"
        )

        val DEFAULT_BAND_FREQUENCIES_HZ = EqualizerDspConfig.DEFAULT_BAND_FREQUENCIES_HZ

        fun presetCurveForIndex(presetIndex: Int): List<Int> = when (presetIndex) {
            1 -> listOf(600, 400, 0, 200, 300)     // Bass & Warmth
            2 -> listOf(-200, 100, 500, 400, 100)  // Vocal Clarity
            3 -> listOf(300, 150, -100, 250, 450)  // Acoustic Studio
            4 -> listOf(500, 200, -150, 350, 600)  // Cinema Wide
            else -> listOf(0, 0, 0, 0, 0)          // Flat
        }

        fun defaultBandSpecs(savedLevelsMb: List<Int> = emptyList()): List<EqualizerBandSpec> {
            return DEFAULT_BAND_FREQUENCIES_HZ.mapIndexed { idx, freq ->
                EqualizerBandSpec(
                    bandIndex = idx,
                    centerFreqHz = freq,
                    minLevelMb = EqualizerDspConfig.MIN_BAND_LEVEL_MB,
                    maxLevelMb = EqualizerDspConfig.MAX_BAND_LEVEL_MB,
                    currentLevelMb = savedLevelsMb.getOrElse(idx) { 0 }
                        .coerceIn(EqualizerDspConfig.MIN_BAND_LEVEL_MB, EqualizerDspConfig.MAX_BAND_LEVEL_MB)
                )
            }
        }
    }
}

/**
 * Coordinates the primary real-time Media3 [PulseEqualizerAudioProcessor] (5-band cascaded biquad
 * DSP + low-shelf Bass Boost + Stereo L/R Balance + Preamp + soft-knee limiter) with optional
 * Android hardware [Equalizer] and [BassBoost] effects.
 */
@OptIn(UnstableApi::class)
class EqualizerController(
    val audioProcessor: PulseEqualizerAudioProcessor = sharedAudioProcessor,
    private var preferHardwareEffect: Boolean = false
) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var boundSessionId: Int = 0
    private var hardwareSessionOperational: Boolean = false
    private var hardwareBassOperational: Boolean = false

    private val _state = MutableStateFlow(HardwareEqualizerState())
    val state: StateFlow<HardwareEqualizerState> = _state.asStateFlow()

    private val descriptorSupported: Boolean by lazy {
        runCatching {
            val descriptors = AudioEffect.queryEffects() ?: emptyArray()
            descriptors.any { it.type == AudioEffect.EFFECT_TYPE_EQUALIZER }
        }.getOrDefault(false)
    }

    init {
        syncDspAndState()
    }

    fun setPreferHardwareEffect(preferHardware: Boolean) {
        if (this.preferHardwareEffect != preferHardware) {
            this.preferHardwareEffect = preferHardware
            val current = _state.value
            bindToAudioSession(
                audioSessionId = current.activeAudioSessionId,
                enabled = current.isEnabled,
                presetIndex = current.selectedPresetIndex,
                bassStrength = current.bassBoostStrength,
                savedBandLevelsMb = current.bands.map { it.currentLevelMb },
                isPlaybackActive = current.isPlaybackActive
            )
        }
    }

    fun updatePlaybackActive(isPlaybackActive: Boolean) {
        if (_state.value.isPlaybackActive != isPlaybackActive) {
            _state.value = _state.value.copy(isPlaybackActive = isPlaybackActive)
            syncDspAndState()
        }
    }

    fun setStereoBalance(balance: Float) {
        val clamped = if (balance.isFinite()) balance.coerceIn(-1.0f, 1.0f) else 0.0f
        if (_state.value.stereoBalance != clamped) {
            _state.value = _state.value.copy(stereoBalance = clamped)
            syncDspAndState()
        }
    }

    fun setPreampGainDb(gainDb: Float) {
        val clamped = if (gainDb.isFinite()) gainDb.coerceIn(-6.0f, 6.0f) else 0.0f
        if (_state.value.preampGainDb != clamped) {
            _state.value = _state.value.copy(preampGainDb = clamped)
            syncDspAndState()
        }
    }

    fun bindToAudioSession(
        audioSessionId: Int,
        enabled: Boolean,
        presetIndex: Int,
        bassStrength: Int,
        savedBandLevelsMb: List<Int>,
        isPlaybackActive: Boolean = _state.value.isPlaybackActive || audioSessionId > 0
    ) {
        val sanitizedPreset = presetIndex.coerceIn(0, HardwareEqualizerState.DEFAULT_PRESET_NAMES.lastIndex)
        val sanitizedBass = bassStrength.coerceIn(0, EqualizerDspConfig.MAX_BASS_BOOST_STRENGTH)
        val resolvedBands = buildBandSpecs(savedBandLevelsMb, sanitizedPreset)

        if (audioSessionId <= 0) {
            releaseHardwareEffectsOnly()
            boundSessionId = 0
            hardwareSessionOperational = false
            hardwareBassOperational = false

            _state.value = _state.value.copy(
                isSupportedOnDevice = true,
                isHardwareEffectSupportedOnDevice = descriptorSupported,
                isBassBoostSupported = true,
                activeAudioSessionId = 0,
                isPlaybackActive = isPlaybackActive,
                isEnabled = enabled,
                selectedPresetIndex = sanitizedPreset,
                bands = resolvedBands,
                bassBoostStrength = sanitizedBass
            )
            syncDspAndState()
            return
        }

        if (boundSessionId != audioSessionId) {
            releaseHardwareEffectsOnly()
            boundSessionId = audioSessionId
        }

        if (preferHardwareEffect && descriptorSupported) {
            try {
                if (equalizer == null) {
                    equalizer = Equalizer(0, audioSessionId)
                }
                val eq = equalizer!!
                val numBands = eq.numberOfBands.toInt().coerceAtLeast(1)
                val range = eq.bandLevelRange
                val minMb = range.getOrElse(0) { (-1500).toShort() }.toInt()
                val maxMb = range.getOrElse(1) { 1500.toShort() }.toInt()

                for (b in 0 until minOf(numBands, resolvedBands.size)) {
                    val target = resolvedBands[b].currentLevelMb.coerceIn(minMb, maxMb)
                    runCatching { eq.setBandLevel(b.toShort(), target.toShort()) }
                }
                eq.enabled = enabled
                hardwareSessionOperational = true

                runCatching {
                    if (bassBoost == null) {
                        bassBoost = BassBoost(0, audioSessionId)
                    }
                    val bb = bassBoost!!
                    hardwareBassOperational = bb.strengthSupported
                    bb.enabled = enabled && hardwareBassOperational
                    if (hardwareBassOperational) {
                        bb.setStrength(sanitizedBass.toShort())
                    }
                }.onFailure {
                    hardwareBassOperational = false
                }
            } catch (_: Throwable) {
                releaseHardwareEffectsOnly()
                hardwareSessionOperational = false
                hardwareBassOperational = false
            }
        } else {
            releaseHardwareEffectsOnly()
            hardwareSessionOperational = false
            hardwareBassOperational = false
        }

        _state.value = _state.value.copy(
            isSupportedOnDevice = true,
            isHardwareEffectSupportedOnDevice = descriptorSupported || hardwareSessionOperational,
            isBassBoostSupported = true,
            activeAudioSessionId = audioSessionId,
            isPlaybackActive = isPlaybackActive,
            isEnabled = enabled,
            selectedPresetIndex = sanitizedPreset,
            bands = resolvedBands,
            bassBoostStrength = sanitizedBass
        )
        syncDspAndState()
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int): List<Int> {
        val currentBands = _state.value.bands.toMutableList()
        if (bandIndex in currentBands.indices) {
            val spec = currentBands[bandIndex]
            val clamped = levelMb.coerceIn(spec.minLevelMb, spec.maxLevelMb)
            if (hardwareSessionOperational && preferHardwareEffect) {
                runCatching {
                    equalizer?.setBandLevel(bandIndex.toShort(), clamped.toShort())
                }
            }
            currentBands[bandIndex] = spec.copy(currentLevelMb = clamped)
            _state.value = _state.value.copy(bands = currentBands)
            syncDspAndState()
        }
        return currentBands.map { it.currentLevelMb }
    }

    fun applyPreset(presetIndex: Int): List<Int> {
        val safePreset = presetIndex.coerceIn(0, (_state.value.presets.size - 1).coerceAtLeast(0))
        val curve = HardwareEqualizerState.presetCurveForIndex(safePreset)
        val currentBands = _state.value.bands.toMutableList()

        for (i in currentBands.indices) {
            val spec = currentBands[i]
            val targetLevel = curve.getOrElse(i) { 0 }.coerceIn(spec.minLevelMb, spec.maxLevelMb)
            if (hardwareSessionOperational && preferHardwareEffect) {
                runCatching {
                    equalizer?.setBandLevel(i.toShort(), targetLevel.toShort())
                }
            }
            currentBands[i] = spec.copy(currentLevelMb = targetLevel)
        }

        _state.value = _state.value.copy(
            selectedPresetIndex = safePreset,
            bands = currentBands
        )
        syncDspAndState()
        return currentBands.map { it.currentLevelMb }
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, EqualizerDspConfig.MAX_BASS_BOOST_STRENGTH)
        if (hardwareSessionOperational && hardwareBassOperational && preferHardwareEffect) {
            runCatching {
                bassBoost?.setStrength(clamped.toShort())
            }
        }
        _state.value = _state.value.copy(bassBoostStrength = clamped)
        syncDspAndState()
    }

    fun setEnabled(enabled: Boolean) {
        if (hardwareSessionOperational && preferHardwareEffect) {
            runCatching {
                equalizer?.enabled = enabled
                bassBoost?.enabled = enabled && hardwareBassOperational
            }
        }
        _state.value = _state.value.copy(isEnabled = enabled)
        syncDspAndState()
    }

    private fun syncDspAndState() {
        val current = _state.value
        val useHardware = preferHardwareEffect &&
            hardwareSessionOperational &&
            current.activeAudioSessionId > 0 &&
            (current.bassBoostStrength == 0 || hardwareBassOperational)

        if (!useHardware) {
            runCatching {
                equalizer?.enabled = false
                bassBoost?.enabled = false
            }
        }

        val dspConfig = EqualizerDspConfig(
            isEnabled = current.isEnabled,
            bypassForHardwareEffect = useHardware,
            bandLevelsMb = current.bands.map { it.currentLevelMb },
            bandFrequenciesHz = current.bands.map { it.centerFreqHz },
            bassBoostStrength = current.bassBoostStrength,
            stereoBalance = current.stereoBalance,
            preampGainDb = current.preampGainDb
        )

        val dspUpdatedOk = runCatching {
            audioProcessor.updateDspConfig(dspConfig)
            true
        }.getOrDefault(false)

        val hasActiveStream = current.isPlaybackActive ||
            current.activeAudioSessionId > 0 ||
            audioProcessor.lastConfiguredSampleRateHz > 0

        val mode: EqualizerProcessingMode
        val diag: EqualizerDiagnosticState
        val statusMsg: String

        if (!dspUpdatedOk) {
            mode = EqualizerProcessingMode.BYPASSED
            diag = EqualizerDiagnosticState.INITIALIZATION_FAILED
            statusMsg = EqualizerDiagnosticState.INITIALIZATION_FAILED.displayText
        } else if (!current.isEnabled) {
            mode = EqualizerProcessingMode.BYPASSED
            diag = EqualizerDiagnosticState.EQUALIZER_DISABLED
            statusMsg = EqualizerDiagnosticState.EQUALIZER_DISABLED.displayText
        } else if (!hasActiveStream) {
            mode = EqualizerProcessingMode.SOFTWARE_BIQUAD_DSP
            diag = EqualizerDiagnosticState.WAITING_FOR_PLAYBACK
            statusMsg = "${EqualizerDiagnosticState.WAITING_FOR_PLAYBACK.displayText} (Software Biquad DSP ready)"
        } else if (useHardware) {
            mode = EqualizerProcessingMode.HARDWARE_AUDIO_EFFECT
            diag = EqualizerDiagnosticState.HARDWARE_EFFECT_ACTIVE
            statusMsg = "${EqualizerDiagnosticState.HARDWARE_EFFECT_ACTIVE.displayText} (Session #${current.activeAudioSessionId})"
        } else if (preferHardwareEffect && !hardwareSessionOperational) {
            mode = EqualizerProcessingMode.SOFTWARE_BIQUAD_DSP
            diag = EqualizerDiagnosticState.HARDWARE_UNSUPPORTED_SOFTWARE_ACTIVE
            statusMsg = EqualizerDiagnosticState.HARDWARE_UNSUPPORTED_SOFTWARE_ACTIVE.displayText
        } else if (!descriptorSupported) {
            mode = EqualizerProcessingMode.SOFTWARE_BIQUAD_DSP
            diag = EqualizerDiagnosticState.HARDWARE_UNSUPPORTED_SOFTWARE_ACTIVE
            statusMsg = EqualizerDiagnosticState.HARDWARE_UNSUPPORTED_SOFTWARE_ACTIVE.displayText
        } else {
            mode = EqualizerProcessingMode.SOFTWARE_BIQUAD_DSP
            diag = EqualizerDiagnosticState.SOFTWARE_PROCESSING_ACTIVE
            val sessionSuffix = if (current.activeAudioSessionId > 0) {
                " • Session #${current.activeAudioSessionId}"
            } else {
                ""
            }
            statusMsg = "${EqualizerDiagnosticState.SOFTWARE_PROCESSING_ACTIVE.displayText}$sessionSuffix"
        }

        _state.value = current.copy(
            isSupportedOnDevice = true,
            isBassBoostSupported = true,
            processingMode = mode,
            diagnosticState = diag,
            statusMessage = statusMsg
        )
    }

    private fun buildBandSpecs(savedLevelsMb: List<Int>, presetIndex: Int): List<EqualizerBandSpec> {
        val fallbackCurve = HardwareEqualizerState.presetCurveForIndex(presetIndex)
        val useSaved = savedLevelsMb.size == HardwareEqualizerState.DEFAULT_BAND_FREQUENCIES_HZ.size
        val levels = if (useSaved) savedLevelsMb else fallbackCurve
        return HardwareEqualizerState.defaultBandSpecs(levels)
    }

    private fun releaseHardwareEffectsOnly() {
        runCatching { equalizer?.enabled = false }
        runCatching { bassBoost?.enabled = false }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    fun releaseEffects() {
        releaseHardwareEffectsOnly()
        boundSessionId = 0
        hardwareSessionOperational = false
        hardwareBassOperational = false
    }

    companion object {
        val sharedAudioProcessor = PulseEqualizerAudioProcessor()
    }
}
