package com.example.alpha.audio

import com.example.alpha.model.AlphaEffectSettings
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
 * Native Kotlin real-time 48kHz audio DSP processor implementing the ALPHA V32.3 signal chain:
 * Input -> HPF & RMS Noise Gate -> 3-Band Biquad EQ -> Dual-Tap Pitch Shifter ->
 * Robot Ring Mod & Comb Resonator -> Monster Sub-Growl -> Tanh Distortion ->
 * Circular Echo Delay -> Master Gain & Soft-Clip Limiter.
 *
 * Pre-allocates all buffers so the real-time audio loop performs zero heap allocations.
 */
class AudioEffectProcessor(sampleRateHz: Int = 48000) {

    @Volatile
    private var sampleRate: Int = sampleRateHz.coerceIn(16000, 96000)

    @Volatile
    private var settings: AlphaEffectSettings = AlphaEffectSettings()

    // Biquad EQ + HPF filters
    private val hpf = BiquadFilter()
    private val lowShelf = BiquadFilter()
    private val midPeak = BiquadFilter()
    private val highShelf = BiquadFilter()
    private val monsterLowPass = BiquadFilter()

    // Track last EQ params to recompute coefficients only when needed
    private var cachedLowDb = Float.NaN
    private var cachedMidDb = Float.NaN
    private var cachedHighDb = Float.NaN
    private var cachedSampleRate = -1

    // Noise gate envelope state
    private var gateEnvelope = 0f
    private var gateGain = 1f

    // Dual-tap pitch shifter ring buffer
    private val pitchBufSize = 8192
    private val pitchBufMask = pitchBufSize - 1
    private val pitchBuffer = FloatArray(pitchBufSize)
    private var pitchWriteIdx = 0
    private var pitchPhaseA = 0f
    private var pitchPhaseB = 0.5f

    // Monster sub-pitch buffer
    private var monsterPhaseA = 0f
    private var monsterPhaseB = 0.5f

    // Robot ring mod & comb filter
    private var robotPhase = 0.0
    private val combBufSize = 2048
    private val combBuffer = FloatArray(combBufSize)
    private var combIdx = 0

    // Echo circular delay line (up to 1 second at 96kHz)
    private val echoBufSize = 96000
    private val echoBuffer = FloatArray(echoBufSize)
    private var echoWriteIdx = 0

    init {
        recomputeFiltersIfNeeded(settings)
    }

    fun setSampleRate(newSampleRate: Int) {
        val clamped = newSampleRate.coerceIn(16000, 96000)
        if (clamped != sampleRate) {
            sampleRate = clamped
            resetState()
        }
    }

    fun updateSettings(newSettings: AlphaEffectSettings) {
        settings = newSettings
    }

    fun getSettings(): AlphaEffectSettings = settings

    fun resetState() {
        hpf.reset()
        lowShelf.reset()
        midPeak.reset()
        highShelf.reset()
        monsterLowPass.reset()
        pitchBuffer.fill(0f)
        combBuffer.fill(0f)
        echoBuffer.fill(0f)
        pitchWriteIdx = 0
        pitchPhaseA = 0f
        pitchPhaseB = 0.5f
        monsterPhaseA = 0f
        monsterPhaseB = 0.5f
        robotPhase = 0.0
        combIdx = 0
        echoWriteIdx = 0
        gateEnvelope = 0f
        gateGain = 1f
        cachedSampleRate = -1
    }

    /**
     * Processes PCM-16 samples in place and returns output RMS level [0..1].
     */
    fun processBufferInPlace(pcmShorts: ShortArray, count: Int): Pair<Float, Float> {
        val current = settings
        recomputeFiltersIfNeeded(current)

        var inputSumSq = 0.0
        var outputSumSq = 0.0
        val sr = sampleRate.toFloat()

        // Bypass quickly if disabled or clean Original Voice with unity gain
        val isBypass = !current.enabled || (
            current.presetId == "original" &&
                abs(current.gain - 1f) < 0.01f &&
                abs(current.pitchSemitones) < 0.05f &&
                abs(current.eqLowDb) < 0.1f &&
                abs(current.eqMidDb) < 0.1f &&
                abs(current.eqHighDb) < 0.1f &&
                current.echoMix < 0.01f &&
                current.distortion < 0.01f &&
                current.noiseGate < 0.01f &&
                current.robotMix < 0.01f &&
                current.monsterAmount < 0.01f
            )

        val pitchWindow = 2048f
        val pitchRatio = 2.0f.pow(current.pitchSemitones / 12.0f)
        val pitchActive = abs(current.pitchSemitones) >= 0.05f
        val pitchStep = (1.0f - pitchRatio) / pitchWindow

        val monsterActive = current.monsterAmount >= 0.02f
        val monsterRatio = 0.62f
        val monsterStep = (1.0f - monsterRatio) / pitchWindow

        val gateThresh = 0.0008f + current.noiseGate * current.noiseGate * 0.085f
        val echoSamples = ((current.echoDelayMs / 1000.0f) * sr).toInt().coerceIn(64, echoBufSize - 1)
        val echoFb = current.echoFeedback.coerceIn(0f, 0.82f)
        val echoMix = current.echoMix.coerceIn(0f, 1f)

        val robotActive = current.robotMix >= 0.01f
        val robotInc = (2.0 * PI * current.robotFreqHz.coerceIn(25f, 220f)) / sr
        val combDelay = (sr / current.robotFreqHz.coerceIn(30f, 220f)).toInt().coerceIn(48, combBufSize - 1)

        val distortion = current.distortion.coerceIn(0f, 1f)
        val distDrive = 1.0f + distortion * 13.0f
        val distNorm = tanh(distDrive.toDouble()).toFloat().coerceAtLeast(0.1f)

        for (i in 0 until count) {
            val dry = pcmShorts[i] / 32768.0f
            inputSumSq += (dry * dry).toDouble()

            if (isBypass) {
                outputSumSq += (dry * dry).toDouble()
                continue
            }

            var sample = dry

            // 1. Noise Gate + Rumble High-Pass
            if (current.noiseGate > 0.01f) {
                sample = hpf.process(sample)
                val level = abs(sample)
                gateEnvelope = if (level > gateEnvelope) {
                    gateEnvelope * 0.92f + level * 0.08f
                } else {
                    gateEnvelope * 0.9992f + level * 0.0008f
                }
                val targetGain = if (gateEnvelope >= gateThresh) 1.0f else (gateEnvelope / gateThresh).coerceIn(0.04f, 1f)
                gateGain = if (targetGain > gateGain) {
                    gateGain * 0.90f + targetGain * 0.10f
                } else {
                    gateGain * 0.996f + targetGain * 0.004f
                }
                sample *= gateGain
            }

            // 2. 3-Band Biquad EQ
            if (abs(current.eqLowDb) >= 0.1f) sample = lowShelf.process(sample)
            if (abs(current.eqMidDb) >= 0.1f) sample = midPeak.process(sample)
            if (abs(current.eqHighDb) >= 0.1f) sample = highShelf.process(sample)

            // Write pre-pitch sample to ring buffer
            pitchBuffer[pitchWriteIdx] = sample

            // 3. Dual-Tap Pitch Shifter
            if (pitchActive) {
                sample = readPitchTap(pitchPhaseA, pitchWindow) * hannWindow(pitchPhaseA) +
                    readPitchTap(pitchPhaseB, pitchWindow) * hannWindow(pitchPhaseB)

                pitchPhaseA += pitchStep
                pitchPhaseB += pitchStep
                if (pitchPhaseA >= 1.0f) pitchPhaseA -= 1.0f else if (pitchPhaseA < 0.0f) pitchPhaseA += 1.0f
                if (pitchPhaseB >= 1.0f) pitchPhaseB -= 1.0f else if (pitchPhaseB < 0.0f) pitchPhaseB += 1.0f
            }

            // 4. Monster Deep Growl (Sub-octave blend + dark low-pass + grit)
            if (monsterActive) {
                val sub = readPitchTap(monsterPhaseA, pitchWindow) * hannWindow(monsterPhaseA) +
                    readPitchTap(monsterPhaseB, pitchWindow) * hannWindow(monsterPhaseB)
                monsterPhaseA += monsterStep
                monsterPhaseB += monsterStep
                if (monsterPhaseA >= 1.0f) monsterPhaseA -= 1.0f else if (monsterPhaseA < 0.0f) monsterPhaseA += 1.0f
                if (monsterPhaseB >= 1.0f) monsterPhaseB -= 1.0f else if (monsterPhaseB < 0.0f) monsterPhaseB += 1.0f

                val filteredSub = monsterLowPass.process(sub)
                val amt = current.monsterAmount.coerceIn(0f, 1f)
                val growl = tanh((sample * (1f - amt * 0.35f) + filteredSub * (0.75f * amt)) * (1f + amt * 1.4f))
                sample = sample * (1f - amt * 0.5f) + growl * (amt * 0.85f + 0.15f)
            }

            pitchWriteIdx = (pitchWriteIdx + 1) and pitchBufMask

            // 5. Robot Ring Modulator + Metallic Comb
            if (robotActive) {
                val carrier = sin(robotPhase).toFloat()
                robotPhase += robotInc
                if (robotPhase > 2.0 * PI) robotPhase -= 2.0 * PI

                val combReadIdx = (combIdx - combDelay + combBufSize) % combBufSize
                val combDelayed = combBuffer[combReadIdx]
                val combOut = sample + combDelayed * 0.45f
                combBuffer[combIdx] = combOut.coerceIn(-1.5f, 1.5f)
                combIdx = (combIdx + 1) % combBufSize

                val robotWet = (sample * carrier * 0.85f) + (combOut * 0.35f)
                val mix = current.robotMix.coerceIn(0f, 1f)
                sample = sample * (1f - mix) + robotWet * mix
            }

            // 6. Tanh Overdrive Distortion
            if (distortion > 0.01f) {
                val saturated = tanh((sample * distDrive).toDouble()).toFloat() / distNorm
                sample = sample * (1f - distortion) + saturated * distortion
            }

            // 7. Circular Ring-Buffer Echo / Delay
            if (echoMix > 0.01f || echoFb > 0.01f) {
                val readIdx = (echoWriteIdx - echoSamples + echoBufSize) % echoBufSize
                val delayed = echoBuffer[readIdx]
                echoBuffer[echoWriteIdx] = (sample + delayed * echoFb).coerceIn(-1.5f, 1.5f)
                echoWriteIdx = (echoWriteIdx + 1) % echoBufSize
                sample = sample * (1f - echoMix * 0.4f) + delayed * echoMix
            } else {
                echoBuffer[echoWriteIdx] = 0f
                echoWriteIdx = (echoWriteIdx + 1) % echoBufSize
            }

            // 8. Master Gain + Soft-Clip Limiter
            sample *= current.gain.coerceIn(0f, 3.0f)
            if (current.limiterEnabled) {
                val mag = abs(sample)
                if (mag > 0.82f) {
                    val sign = if (sample >= 0f) 1f else -1f
                    sample = sign * (0.82f + 0.17f * tanh(((mag - 0.82f) / 0.17f).toDouble()).toFloat())
                }
            }
            val clamped = sample.coerceIn(-0.99f, 0.99f)
            outputSumSq += (clamped * clamped).toDouble()
            pcmShorts[i] = (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
        }

        val invCount = if (count > 0) 1.0 / count else 1.0
        val inRms = sqrt(inputSumSq * invCount).toFloat().coerceIn(0f, 1f)
        val outRms = sqrt(outputSumSq * invCount).toFloat().coerceIn(0f, 1f)
        return inRms to outRms
    }

    private fun hannWindow(phase: Float): Float {
        return 0.5f * (1.0f - cos(2.0 * PI * phase).toFloat())
    }

    private fun readPitchTap(phase: Float, windowSamples: Float): Float {
        val delay = phase * windowSamples
        val readPos = pitchWriteIdx - delay
        val idx0 = (kotlin.math.floor(readPos).toInt() % pitchBufSize + pitchBufSize) and pitchBufMask
        val idx1 = (idx0 + 1) and pitchBufMask
        val frac = readPos - kotlin.math.floor(readPos)
        return pitchBuffer[idx0] * (1f - frac) + pitchBuffer[idx1] * frac
    }

    private fun recomputeFiltersIfNeeded(s: AlphaEffectSettings) {
        if (cachedSampleRate == sampleRate &&
            cachedLowDb == s.eqLowDb &&
            cachedMidDb == s.eqMidDb &&
            cachedHighDb == s.eqHighDb
        ) {
            return
        }
        val sr = sampleRate.toDouble()
        hpf.configureHighPass(sr, freqHz = 85.0, q = 0.707)
        lowShelf.configureLowShelf(sr, freqHz = 140.0, gainDb = s.eqLowDb.toDouble(), slope = 0.9)
        midPeak.configurePeaking(sr, freqHz = 1050.0, q = 0.85, gainDb = s.eqMidDb.toDouble())
        highShelf.configureHighShelf(sr, freqHz = 4400.0, gainDb = s.eqHighDb.toDouble(), slope = 0.9)
        monsterLowPass.configureLowPass(sr, freqHz = 420.0, q = 0.8)

        cachedSampleRate = sampleRate
        cachedLowDb = s.eqLowDb
        cachedMidDb = s.eqMidDb
        cachedHighDb = s.eqHighDb
    }

    private class BiquadFilter {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f

        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            val safeY = if (abs(y) < 1e-18f || y.isNaN()) 0f else y
            x2 = x1
            x1 = x
            y2 = y1
            y1 = safeY
            return safeY
        }

        fun configureHighPass(sr: Double, freqHz: Double, q: Double) {
            val w0 = 2.0 * PI * freqHz / sr
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
            b1 = (-(1.0 + cosW0) / a0).toFloat()
            b2 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun configureLowPass(sr: Double, freqHz: Double, q: Double) {
            val w0 = 2.0 * PI * freqHz / sr
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
            b1 = ((1.0 - cosW0) / a0).toFloat()
            b2 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun configurePeaking(sr: Double, freqHz: Double, q: Double, gainDb: Double) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sr
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)
            val a0 = 1.0 + alpha / a
            b0 = ((1.0 + alpha * a) / a0).toFloat()
            b1 = (-2.0 * cosW0 / a0).toFloat()
            b2 = ((1.0 - alpha * a) / a0).toFloat()
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha / a) / a0).toFloat()
        }

        fun configureLowShelf(sr: Double, freqHz: Double, gainDb: Double, slope: Double) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sr
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
            b0 = ((a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0).toFloat()
            b1 = ((2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)) / a0).toFloat()
            b2 = ((a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0).toFloat()
            a1 = ((-2.0 * ((a - 1.0) + (a + 1.0) * cosW0)) / a0).toFloat()
            a2 = (((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        }

        fun configureHighShelf(sr: Double, freqHz: Double, gainDb: Double, slope: Double) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sr
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
            b0 = ((a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0).toFloat()
            b1 = ((-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)) / a0).toFloat()
            b2 = ((a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0).toFloat()
            a1 = ((2.0 * ((a - 1.0) - (a + 1.0) * cosW0)) / a0).toFloat()
            a2 = (((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        }
    }
}
