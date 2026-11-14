package com.example.alpha.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.example.alpha.model.AlphaEffectSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

enum class EngineMode {
    IDLE,
    LIVE_MONITOR,
    RECORDING_TEST,
    PLAYING_RECORDING
}

data class AudioEngineState(
    val mode: EngineMode = EngineMode.IDLE,
    val sampleRateHz: Int = 48000,
    val bufferFrames: Int = 512,
    val estimatedLatencyMs: Float = 10.7f,
    val inputRms: Float = 0f,
    val outputRms: Float = 0f,
    val waveform: List<Float> = List(28) { 0.05f },
    val isClientSilencedBySystem: Boolean = false,
    val activeInputDeviceName: String = "Built-in Mic",
    val activeOutputDeviceName: String = "Device Speaker",
    val isHeadsetConnected: Boolean = false,
    val lastRecordingPath: String? = null,
    val lastRecordingDurationSec: Float = 0f,
    val statusMessage: String = "Engine ready (48 kHz low-latency PCM)"
)

class AlphaAudioEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val processor = AudioEffectProcessor(48000)

    private val _state = MutableStateFlow(AudioEngineState())
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    private var workerJob: Job? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val recordedSamples = ByteArrayOutputStream()

    fun updateSettings(settings: AlphaEffectSettings) {
        processor.updateSettings(settings)
    }

    fun refreshDeviceInfo() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val headset = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val activeOut = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        } ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val activeIn = inputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        } ?: inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

        _state.value = _state.value.copy(
            isHeadsetConnected = headset,
            activeInputDeviceName = activeIn?.productName?.toString()?.ifBlank { "Microphone" } ?: "Built-in Mic",
            activeOutputDeviceName = activeOut?.productName?.toString()?.ifBlank { "Audio Output" } ?: "Speaker"
        )
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun startLiveMonitor(preferredSampleRate: Int = 48000, lowLatency: Boolean = true) {
        startCaptureLoop(recordToFile = false, preferredSampleRate = preferredSampleRate, lowLatency = lowLatency)
    }

    fun startRecordingTest(preferredSampleRate: Int = 48000, lowLatency: Boolean = true) {
        startCaptureLoop(recordToFile = true, preferredSampleRate = preferredSampleRate, lowLatency = lowLatency)
    }

    fun stopAll() {
        workerJob?.cancel()
        workerJob = null
        _state.value = _state.value.copy(
            mode = EngineMode.IDLE,
            inputRms = 0f,
            outputRms = 0f,
            waveform = List(28) { 0.05f },
            isClientSilencedBySystem = false,
            statusMessage = "Engine idle"
        )
    }

    fun playLastRecording() {
        val path = _state.value.lastRecordingPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _state.value = _state.value.copy(statusMessage = "Recording file not found")
            return
        }
        stopAll()
        workerJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var track: AudioTrack? = null
            try {
                val bytes = file.readBytes()
                if (bytes.size <= 44) {
                    _state.value = _state.value.copy(statusMessage = "Recording is empty")
                    return@launch
                }
                val sr = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(16000, 96000)
                val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuf, 2048))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                _state.value = _state.value.copy(
                    mode = EngineMode.PLAYING_RECORDING,
                    statusMessage = "Playing processed WAV (${sr} Hz)..."
                )

                var offset = 44
                val chunk = 1024
                while (isActive && offset < bytes.size) {
                    val len = minOf(chunk, bytes.size - offset)
                    track.write(bytes, offset, len)
                    val previewBars = computeWaveformFromBytes(bytes, offset, len)
                    _state.value = _state.value.copy(
                        waveform = previewBars,
                        outputRms = previewBars.average().toFloat()
                    )
                    offset += len
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(statusMessage = "Playback error: ${e.message}")
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
                _state.value = _state.value.copy(
                    mode = EngineMode.IDLE,
                    outputRms = 0f,
                    waveform = List(28) { 0.05f },
                    statusMessage = "Playback finished"
                )
            }
        }
    }

    private fun startCaptureLoop(recordToFile: Boolean, preferredSampleRate: Int, lowLatency: Boolean) {
        if (!hasRecordPermission()) {
            _state.value = _state.value.copy(statusMessage = "RECORD_AUDIO permission required")
            return
        }
        stopAll()
        refreshDeviceInfo()

        workerJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val chosenRate = resolveSupportedSampleRate(preferredSampleRate)
            processor.setSampleRate(chosenRate)
            processor.resetState()

            val minRecBuf = AudioRecord.getMinBufferSize(
                chosenRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val minPlayBuf = AudioTrack.getMinBufferSize(
                chosenRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val frameShorts = if (lowLatency) 512 else 1024
            val recordBufferBytes = max(minRecBuf, frameShorts * 4)
            val trackBufferBytes = max(minPlayBuf, frameShorts * 4)

            var audioRecord: AudioRecord? = null
            var audioTrack: AudioTrack? = null

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    chosenRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufferBytes
                )
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release()
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        chosenRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        recordBufferBytes
                    )
                }

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    _state.value = _state.value.copy(
                        mode = EngineMode.IDLE,
                        statusMessage = "Unable to initialize AudioRecord at $chosenRate Hz"
                    )
                    return@launch
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cb = object : AudioManager.AudioRecordingCallback() {
                        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                            val mine = configs.firstOrNull { it.clientAudioSessionId == audioRecord.audioSessionId }
                            val silenced = mine?.isClientSilenced == true
                            _state.value = _state.value.copy(isClientSilencedBySystem = silenced)
                        }
                    }
                    recordingCallback = cb
                    audioRecord.registerAudioRecordingCallback(callbackExecutor, cb)
                }

                if (!recordToFile) {
                    val trackBuilder = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(chosenRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(trackBufferBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lowLatency) {
                        trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                    audioTrack = trackBuilder.build()
                    audioTrack.play()
                } else {
                    recordedSamples.reset()
                }

                audioRecord.startRecording()

                val estLatency = (frameShorts.toFloat() / chosenRate) * 1000f * 2f
                _state.value = _state.value.copy(
                    mode = if (recordToFile) EngineMode.RECORDING_TEST else EngineMode.LIVE_MONITOR,
                    sampleRateHz = chosenRate,
                    bufferFrames = frameShorts,
                    estimatedLatencyMs = estLatency,
                    isClientSilencedBySystem = false,
                    statusMessage = if (recordToFile) {
                        "Recording processed voice ($chosenRate Hz)..."
                    } else {
                        "Live DSP monitor active ($chosenRate Hz)"
                    }
                )

                val buffer = ShortArray(frameShorts)
                val byteBuf = ByteBuffer.allocate(frameShorts * 2).order(ByteOrder.LITTLE_ENDIAN)

                while (isActive && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = audioRecord.read(buffer, 0, frameShorts)
                    if (readCount > 0) {
                        val (inRms, outRms) = processor.processBufferInPlace(buffer, readCount)

                        if (recordToFile) {
                            byteBuf.clear()
                            for (i in 0 until readCount) {
                                byteBuf.putShort(buffer[i])
                            }
                            recordedSamples.write(byteBuf.array(), 0, readCount * 2)
                        } else {
                            audioTrack?.write(buffer, 0, readCount, AudioTrack.WRITE_BLOCKING)
                        }

                        val bars = downsampleWaveform(buffer, readCount, 28)
                        _state.value = _state.value.copy(
                            inputRms = inRms,
                            outputRms = outRms,
                            waveform = bars
                        )
                    }
                }

                if (recordToFile && recordedSamples.size() > 0) {
                    val outDir = File(context.filesDir, "recordings").apply { mkdirs() }
                    val outFile = File(outDir, "alpha_preview_${System.currentTimeMillis()}.wav")
                    val pcmData = recordedSamples.toByteArray()
                    writeWavFile(outFile, pcmData, chosenRate)
                    val durationSec = pcmData.size / (chosenRate * 2.0f)
                    _state.value = _state.value.copy(
                        lastRecordingPath = outFile.absolutePath,
                        lastRecordingDurationSec = durationSec,
                        statusMessage = "Saved ${"%.1f".format(durationSec)}s processed WAV"
                    )
                }
            } catch (e: SecurityException) {
                _state.value = _state.value.copy(statusMessage = "Mic permission denied: ${e.message}")
            } catch (e: Exception) {
                _state.value = _state.value.copy(statusMessage = "Audio engine error: ${e.message}")
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    recordingCallback?.let { cb ->
                        runCatching { audioRecord?.unregisterAudioRecordingCallback(cb) }
                    }
                    recordingCallback = null
                }
                runCatching { audioRecord?.stop() }
                runCatching { audioRecord?.release() }
                runCatching { audioTrack?.stop() }
                runCatching { audioTrack?.release() }
                _state.value = _state.value.copy(
                    mode = EngineMode.IDLE,
                    inputRms = 0f,
                    outputRms = 0f,
                    waveform = List(28) { 0.05f }
                )
            }
        }
    }

    private fun resolveSupportedSampleRate(preferred: Int): Int {
        val candidates = listOf(preferred, 48000, 44100, 16000).distinct()
        for (rate in candidates) {
            val minSize = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minSize > 0) return rate
        }
        return 44100
    }

    private fun downsampleWaveform(samples: ShortArray, count: Int, bars: Int): List<Float> {
        val step = max(1, count / bars)
        return List(bars) { idx ->
            val start = idx * step
            val end = minOf(count, start + step)
            var peak = 0f
            for (i in start until end) {
                val amp = abs(samples[i] / 32768f)
                if (amp > peak) peak = amp
            }
            (peak * 1.4f).coerceIn(0.05f, 1.0f)
        }
    }

    private fun computeWaveformFromBytes(bytes: ByteArray, offset: Int, len: Int): List<Float> {
        val sampleCount = len / 2
        val shorts = ShortArray(sampleCount)
        ByteBuffer.wrap(bytes, offset, len).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return downsampleWaveform(shorts, sampleCount, 28)
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int) {
        FileOutputStream(file).use { out ->
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size (PCM)
            header.putShort(1) // AudioFormat (PCM = 1)
            header.putShort(1) // NumChannels (Mono = 1)
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(2) // BlockAlign
            header.putShort(16) // BitsPerSample
            header.put("data".toByteArray())
            header.putInt(pcmData.size)
            out.write(header.array())
            out.write(pcmData)
        }
    }
}
