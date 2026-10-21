package com.example.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Filter topology for individual biquad sections in the 5-band Equalizer + Bass Boost pipeline.
 */
enum class BiquadFilterType {
    LOW_SHELF,
    PEAKING_EQ,
    HIGH_SHELF
}

/**
 * Immutable normalized RBJ Audio EQ Cookbook biquad coefficients:
 * H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
 */
data class BiquadCoefficients(
    val b0: Double = 1.0,
    val b1: Double = 0.0,
    val b2: Double = 0.0,
    val a1: Double = 0.0,
    val a2: Double = 0.0
) {
    /**
     * Verifies all coefficients are finite and poles lie strictly inside the unit circle:
     * Jury stability criteria for 2nd-order real polynomial 1 + a1*z^-1 + a2*z^-2:
     * |a2| < 1.0 and |a1| < 1.0 + a2
     */
    fun isStable(): Boolean {
        if (!b0.isFinite() || !b1.isFinite() || !b2.isFinite() || !a1.isFinite() || !a2.isFinite()) {
            return false
        }
        return abs(a2) < 0.999999 && abs(a1) < (1.0 + a2 - 1e-6)
    }

    companion object {
        val IDENTITY = BiquadCoefficients()

        fun design(
            type: BiquadFilterType,
            centerFreqHz: Double,
            sampleRateHz: Double,
            gainDb: Double,
            q: Double = 1.0
        ): BiquadCoefficients {
            if (sampleRateHz <= 0.0 || !centerFreqHz.isFinite() || !gainDb.isFinite() || !q.isFinite()) {
                return IDENTITY
            }
            val clampedGainDb = gainDb.coerceIn(-15.0, 15.0)
            if (abs(clampedGainDb) < 0.01) {
                return IDENTITY
            }

            // Clamp center frequency safely inside (20 Hz .. 0.45 * Nyquist*2) to prevent warping instability
            val nyquistSafeMax = max(100.0, sampleRateHz * 0.45)
            val f0 = centerFreqHz.coerceIn(20.0, nyquistSafeMax)
            val safeQ = q.coerceIn(0.35, 4.0)

            val a = 10.0.pow(clampedGainDb / 40.0) // sqrt(10^(dB/20))
            val w0 = 2.0 * PI * f0 / sampleRateHz
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * safeQ)

            val rawB0: Double
            val rawB1: Double
            val rawB2: Double
            val rawA0: Double
            val rawA1: Double
            val rawA2: Double

            when (type) {
                BiquadFilterType.PEAKING_EQ -> {
                    rawB0 = 1.0 + alpha * a
                    rawB1 = -2.0 * cosW0
                    rawB2 = 1.0 - alpha * a
                    rawA0 = 1.0 + alpha / a
                    rawA1 = -2.0 * cosW0
                    rawA2 = 1.0 - alpha / a
                }
                BiquadFilterType.LOW_SHELF -> {
                    val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                    rawB0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
                    rawB1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
                    rawB2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
                    rawA0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
                    rawA1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
                    rawA2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha
                }
                BiquadFilterType.HIGH_SHELF -> {
                    val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                    rawB0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
                    rawB1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
                    rawB2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
                    rawA0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
                    rawA1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
                    rawA2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha
                }
            }

            if (abs(rawA0) < 1e-12) return IDENTITY

            val candidate = BiquadCoefficients(
                b0 = rawB0 / rawA0,
                b1 = rawB1 / rawA0,
                b2 = rawB2 / rawA0,
                a1 = rawA1 / rawA0,
                a2 = rawA2 / rawA0
            )
            return if (candidate.isStable()) candidate else IDENTITY
        }
    }
}

/**
 * Immutable snapshot of the Equalizer + Bass Boost DSP configuration.
 */
data class EqualizerDspConfig(
    val isEnabled: Boolean = false,
    val bypassForHardwareEffect: Boolean = false,
    val bandLevelsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    val bandFrequenciesHz: List<Int> = DEFAULT_BAND_FREQUENCIES_HZ,
    val bassBoostStrength: Int = 0 // 0..1000
) {
    val isEffectivelyActive: Boolean
        get() = isEnabled && !bypassForHardwareEffect

    val hasNonZeroAdjustments: Boolean
        get() = bandLevelsMb.any { it != 0 } || bassBoostStrength > 0

    companion object {
        val DEFAULT_BAND_FREQUENCIES_HZ = listOf(60, 230, 910, 3600, 14000)
        const val MIN_BAND_LEVEL_MB = -1500
        const val MAX_BAND_LEVEL_MB = 1500
        const val MAX_BASS_BOOST_STRENGTH = 1000
    }
}

/**
 * Multiband cascaded Direct Form II Transposed biquad filter bank with Bass Boost and
 * soft-knee output protection.
 */
class MultiBandBiquadFilterBank(
    sampleRateHz: Int = 44100,
    channelCount: Int = 2
) {
    private var currentSampleRateHz: Int = sampleRateHz.coerceAtLeast(8000)
    private var currentChannelCount: Int = channelCount.coerceIn(1, 8)
    private var activeConfig: EqualizerDspConfig = EqualizerDspConfig()

    // 5 EQ bands + 1 Bass Boost low-shelf section = 6 sections total
    private var sectionCoeffs: Array<BiquadCoefficients> = Array(TOTAL_SECTIONS) { BiquadCoefficients.IDENTITY }
    private var preampLinearGain: Double = 1.0

    // State arrays indexed by [sectionIndex * maxChannels + channel]
    private var z1 = DoubleArray(TOTAL_SECTIONS * 8)
    private var z2 = DoubleArray(TOTAL_SECTIONS * 8)

    init {
        rebuildCoefficients()
    }

    @Synchronized
    fun configureStream(sampleRateHz: Int, channelCount: Int) {
        val safeRate = sampleRateHz.coerceAtLeast(8000)
        val safeChannels = channelCount.coerceIn(1, 8)
        if (safeRate != currentSampleRateHz || safeChannels != currentChannelCount) {
            currentSampleRateHz = safeRate
            currentChannelCount = safeChannels
            resetFilterState()
            rebuildCoefficients()
        }
    }

    @Synchronized
    fun updateConfig(newConfig: EqualizerDspConfig) {
        val sanitizedBands = EqualizerDspConfig.DEFAULT_BAND_FREQUENCIES_HZ.indices.map { idx ->
            newConfig.bandLevelsMb.getOrElse(idx) { 0 }
                .coerceIn(EqualizerDspConfig.MIN_BAND_LEVEL_MB, EqualizerDspConfig.MAX_BAND_LEVEL_MB)
        }
        val sanitizedFreqs = EqualizerDspConfig.DEFAULT_BAND_FREQUENCIES_HZ.mapIndexed { idx, defaultFreq ->
            newConfig.bandFrequenciesHz.getOrElse(idx) { defaultFreq }.coerceIn(20, 20000)
        }
        val sanitizedBass = newConfig.bassBoostStrength.coerceIn(0, EqualizerDspConfig.MAX_BASS_BOOST_STRENGTH)

        val normalized = newConfig.copy(
            bandLevelsMb = sanitizedBands,
            bandFrequenciesHz = sanitizedFreqs,
            bassBoostStrength = sanitizedBass
        )
        if (normalized != activeConfig) {
            val wasActive = activeConfig.isEffectivelyActive
            activeConfig = normalized
            rebuildCoefficients()
            if (!normalized.isEffectivelyActive && wasActive) {
                resetFilterState()
            }
        }
    }

    @Synchronized
    fun getConfig(): EqualizerDspConfig = activeConfig

    @Synchronized
    fun getActiveCoefficients(): List<BiquadCoefficients> = sectionCoeffs.toList()

    @Synchronized
    fun resetFilterState() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    private fun rebuildCoefficients() {
        val cfg = activeConfig
        if (!cfg.isEffectivelyActive) {
            for (i in 0 until TOTAL_SECTIONS) {
                sectionCoeffs[i] = BiquadCoefficients.IDENTITY
            }
            preampLinearGain = 1.0
            return
        }

        val sr = currentSampleRateHz.toDouble()
        var maxPositiveBoostDb = 0.0

        for (bandIdx in 0 until EQ_BAND_COUNT) {
            val freqHz = cfg.bandFrequenciesHz.getOrElse(bandIdx) {
                EqualizerDspConfig.DEFAULT_BAND_FREQUENCIES_HZ[bandIdx]
            }.toDouble()
            val levelMb = cfg.bandLevelsMb.getOrElse(bandIdx) { 0 }
            val gainDb = (levelMb / 100.0).coerceIn(-15.0, 15.0)
            if (gainDb > 0.0) {
                maxPositiveBoostDb += gainDb
            }

            val q = when (bandIdx) {
                0 -> 0.85
                EQ_BAND_COUNT - 1 -> 0.85
                else -> 1.10
            }
            sectionCoeffs[bandIdx] = BiquadCoefficients.design(
                type = BiquadFilterType.PEAKING_EQ,
                centerFreqHz = freqHz,
                sampleRateHz = sr,
                gainDb = gainDb,
                q = q
            )
        }

        // Dedicated Bass Boost section: 0..1000 maps to 0.0..+9.0 dB low-shelf boost (corner at 130 Hz so 60-90 Hz sits on the shelf plateau)
        val bassBoostDb = (cfg.bassBoostStrength.coerceIn(0, 1000) / 1000.0) * 9.0
        sectionCoeffs[BASS_BOOST_SECTION_INDEX] = BiquadCoefficients.design(
            type = BiquadFilterType.LOW_SHELF,
            centerFreqHz = 130.0,
            sampleRateHz = sr,
            gainDb = bassBoostDb,
            q = 0.7071
        )

        // Headroom management: attenuate pre-gain only when multiple overlapping bands are heavily boosted simultaneously
        val combinedBoostDb = maxPositiveBoostDb + bassBoostDb
        val headroomAttenuationDb = if (combinedBoostDb > 18.0) {
            -((combinedBoostDb - 18.0) * 0.25).coerceAtMost(9.0)
        } else {
            0.0
        }
        preampLinearGain = 10.0.pow(headroomAttenuationDb / 20.0)
    }

    /**
     * Processes an array of normalized floating-point samples [-1.0, 1.0] in-place.
     * Interleaved by [ currentChannelCount ].
     */
    @Synchronized
    fun processNormalizedSamplesInPlace(samples: DoubleArray, frameCount: Int = samples.size / currentChannelCount) {
        val cfg = activeConfig
        if (!cfg.isEffectivelyActive || !cfg.hasNonZeroAdjustments) {
            return
        }

        val channels = currentChannelCount
        val totalSamples = min(samples.size, frameCount * channels)
        val preGain = preampLinearGain

        var sampleIndex = 0
        while (sampleIndex < totalSamples) {
            for (ch in 0 until channels) {
                var x = samples[sampleIndex + ch] * preGain
                for (sec in 0 until TOTAL_SECTIONS) {
                    val c = sectionCoeffs[sec]
                    if (c !== BiquadCoefficients.IDENTITY) {
                        val stateIdx = sec * 8 + ch
                        val y = c.b0 * x + z1[stateIdx]
                        z1[stateIdx] = c.b1 * x - c.a1 * y + z2[stateIdx]
                        z2[stateIdx] = c.b2 * x - c.a2 * y
                        x = y
                    }
                }
                samples[sampleIndex + ch] = softLimitSample(x)
            }
            sampleIndex += channels
        }
    }

    /**
     * Processes a 16-bit little-endian PCM [ShortArray] and returns a new processed [ShortArray].
     */
    @Synchronized
    fun processPcm16Array(input: ShortArray): ShortArray {
        val cfg = activeConfig
        if (!cfg.isEffectivelyActive || !cfg.hasNonZeroAdjustments) {
            return input.copyOf()
        }

        val output = ShortArray(input.size)
        val channels = currentChannelCount
        val preGain = preampLinearGain
        var idx = 0
        val limit = input.size - (input.size % channels)

        while (idx < limit) {
            for (ch in 0 until channels) {
                var x = (input[idx + ch].toDouble() / 32768.0) * preGain
                for (sec in 0 until TOTAL_SECTIONS) {
                    val c = sectionCoeffs[sec]
                    if (c !== BiquadCoefficients.IDENTITY) {
                        val stateIdx = sec * 8 + ch
                        val y = c.b0 * x + z1[stateIdx]
                        z1[stateIdx] = c.b1 * x - c.a1 * y + z2[stateIdx]
                        z2[stateIdx] = c.b2 * x - c.a2 * y
                        x = y
                    }
                }
                val limited = softLimitSample(x)
                val pcmInt = (limited * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output[idx + ch] = pcmInt.toShort()
            }
            idx += channels
        }
        while (idx < input.size) {
            output[idx] = input[idx]
            idx++
        }
        return output
    }

    /**
     * Processes a Media3 [ByteBuffer] of PCM_16BIT or PCM_FLOAT samples into [outputBuffer].
     */
    @Synchronized
    fun processByteBuffer(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        encoding: Int
    ) {
        val cfg = activeConfig
        if (!cfg.isEffectivelyActive || !cfg.hasNonZeroAdjustments) {
            outputBuffer.put(inputBuffer)
            return
        }

        val channels = currentChannelCount
        val preGain = preampLinearGain

        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = 2 * channels
                while (inputBuffer.remaining() >= frameBytes) {
                    for (ch in 0 until channels) {
                        val rawShort = inputBuffer.short
                        var x = (rawShort.toDouble() / 32768.0) * preGain
                        for (sec in 0 until TOTAL_SECTIONS) {
                            val c = sectionCoeffs[sec]
                            if (c !== BiquadCoefficients.IDENTITY) {
                                val stateIdx = sec * 8 + ch
                                val y = c.b0 * x + z1[stateIdx]
                                z1[stateIdx] = c.b1 * x - c.a1 * y + z2[stateIdx]
                                z2[stateIdx] = c.b2 * x - c.a2 * y
                                x = y
                            }
                        }
                        val limited = softLimitSample(x)
                        val outShort = (limited * 32767.0).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                        outputBuffer.putShort(outShort)
                    }
                }
                while (inputBuffer.hasRemaining()) {
                    outputBuffer.put(inputBuffer.get())
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = 4 * channels
                while (inputBuffer.remaining() >= frameBytes) {
                    for (ch in 0 until channels) {
                        val rawFloat = inputBuffer.float
                        var x = (if (rawFloat.isFinite()) rawFloat.toDouble() else 0.0) * preGain
                        for (sec in 0 until TOTAL_SECTIONS) {
                            val c = sectionCoeffs[sec]
                            if (c !== BiquadCoefficients.IDENTITY) {
                                val stateIdx = sec * 8 + ch
                                val y = c.b0 * x + z1[stateIdx]
                                z1[stateIdx] = c.b1 * x - c.a1 * y + z2[stateIdx]
                                z2[stateIdx] = c.b2 * x - c.a2 * y
                                x = y
                            }
                        }
                        val limited = softLimitSample(x).toFloat().coerceIn(-1.0f, 1.0f)
                        outputBuffer.putFloat(limited)
                    }
                }
                while (inputBuffer.hasRemaining()) {
                    outputBuffer.put(inputBuffer.get())
                }
            }
            else -> {
                outputBuffer.put(inputBuffer)
            }
        }
    }

    companion object {
        const val EQ_BAND_COUNT = 5
        const val BASS_BOOST_SECTION_INDEX = 5
        const val TOTAL_SECTIONS = 6

        /**
         * Transparent up to |x| <= 0.85, then smooth hyperbolic tangent knee approaching +/-1.0
         * to prevent hard digital clipping on transient peaks.
         */
        fun softLimitSample(sample: Double): Double {
            if (!sample.isFinite()) return 0.0
            val absX = abs(sample)
            if (absX <= 0.85) return sample
            val sign = if (sample >= 0.0) 1.0 else -1.0
            val excess = (absX - 0.85) / 0.15
            val compressed = 0.85 + 0.15 * tanh(excess)
            return (sign * compressed).coerceIn(-0.9999, 0.9999)
        }
    }
}

/**
 * Media3 [AudioProcessor] wired into the ExoPlayer [androidx.media3.exoplayer.audio.DefaultAudioSink]
 * pipeline so all audio playback receives real-time 5-band biquad EQ and Bass Boost processing.
 */
@OptIn(UnstableApi::class)
class PulseEqualizerAudioProcessor : BaseAudioProcessor() {

    private val filterBank = MultiBandBiquadFilterBank()
    private val configRef = AtomicReference(EqualizerDspConfig())
    private val processedFramesCounter = AtomicLong(0L)

    @Volatile
    var lastConfiguredSampleRateHz: Int = 0
        private set

    @Volatile
    var lastConfiguredChannelCount: Int = 0
        private set

    fun updateDspConfig(config: EqualizerDspConfig) {
        configRef.set(config)
        filterBank.updateConfig(config)
    }

    fun getDspConfig(): EqualizerDspConfig = filterBank.getConfig()

    fun getFilterBankForTesting(): MultiBandBiquadFilterBank = filterBank

    fun getProcessedFramesCount(): Long = processedFramesCounter.get()

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount !in 1..8 || inputAudioFormat.sampleRate <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        lastConfiguredSampleRateHz = inputAudioFormat.sampleRate
        lastConfiguredChannelCount = inputAudioFormat.channelCount
        filterBank.configureStream(
            sampleRateHz = inputAudioFormat.sampleRate,
            channelCount = inputAudioFormat.channelCount
        )
        filterBank.updateConfig(configRef.get())
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        inputBuffer.order(ByteOrder.nativeOrder())
        val outputBuffer = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder())

        filterBank.processByteBuffer(
            inputBuffer = inputBuffer,
            outputBuffer = outputBuffer,
            encoding = inputAudioFormat.encoding
        )

        val bytesPerFrame = inputAudioFormat.bytesPerFrame.coerceAtLeast(2)
        processedFramesCounter.addAndGet((remaining / bytesPerFrame).toLong())
        outputBuffer.flip()
    }

    override fun onFlush() {
        filterBank.resetFilterState()
    }

    override fun onReset() {
        filterBank.resetFilterState()
        lastConfiguredSampleRateHz = 0
        lastConfiguredChannelCount = 0
    }
}
