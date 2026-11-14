package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.alpha.audio.AudioEffectProcessor
import com.example.alpha.data.SettingsManager
import com.example.alpha.model.AlphaEffectSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioEffectProcessorTest {

    private fun generateSinePcm(freqHz: Double = 440.0, sampleRate: Int = 48000, count: Int = 2048): ShortArray {
        return ShortArray(count) { i ->
            (sin(2.0 * PI * freqHz * i / sampleRate) * 10000.0).toInt().toShort()
        }
    }

    @Test
    fun `original voice preset bypasses audio untouched`() {
        val processor = AudioEffectProcessor(48000)
        processor.updateSettings(AlphaEffectSettings(presetId = "original", enabled = false))
        val input = generateSinePcm()
        val copy = input.copyOf()
        processor.processBufferInPlace(copy, copy.size)
        assertTrue(input.contentEquals(copy))
    }

    @Test
    fun `gain and distortion genuinely transform waveform`() {
        val processor = AudioEffectProcessor(48000)
        processor.updateSettings(
            AlphaEffectSettings(
                presetId = "custom",
                enabled = true,
                gain = 2.0f,
                distortion = 0.7f,
                robotMix = 0.5f,
                echoMix = 0.3f
            )
        )
        val input = generateSinePcm()
        val processed = input.copyOf()
        val (inRms, outRms) = processor.processBufferInPlace(processed, processed.size)
        assertTrue(inRms > 0.1f)
        assertTrue(outRms > 0.1f)
        assertNotEquals(input.toList(), processed.toList())
    }

    @Test
    fun `bundled alpha v32_3 effects config loads presets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = SettingsManager(context)
        assertEquals("32.3", settingsManager.bundledConfig.version)
        assertTrue(settingsManager.presets.any { it.id == "robot" })
        assertTrue(settingsManager.presets.any { it.id == "monster" })
        assertTrue(settingsManager.presets.any { it.id == "original" })
    }
}
