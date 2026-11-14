package com.example.alpha.shizuku

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Bound via Shizuku UserService API to execute read-only system audio and app state
 * diagnostics using Shizuku's authorized shell privileges (without root or hidden APIs).
 */
class AlphaUserService : IAlphaShizukuService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun runAudioDiagnostics(): String {
        val sb = StringBuilder()
        sb.appendLine("=== SHIZUKU PRIVILEGED AUDIO DIAGNOSTICS ===")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("UID: ${android.os.Process.myUid()}")
        sb.appendLine()
        sb.appendLine("--- Active Audio Focus / Mode (dumpsys audio) ---")
        sb.appendLine(execShell("dumpsys audio | grep -E 'AudioFocus|mMode|Communication|AudioRecord|AudioPolicy' | head -n 28"))
        return sb.toString().trim()
    }

    override fun inspectPackageState(packageName: String): String {
        val sanitized = packageName.replace(Regex("[^a-zA-Z0-9._]"), "")
        if (sanitized.isBlank()) return "Invalid package name"
        val sb = StringBuilder()
        sb.appendLine("=== PACKAGE STATE: $sanitized ===")
        val pathOut = execShell("pm path $sanitized")
        sb.appendLine(if (pathOut.isBlank()) "Package not installed on device" else pathOut)
        val appOpsOut = execShell("cmd appops get $sanitized RECORD_AUDIO")
        sb.appendLine("RECORD_AUDIO appop: ${appOpsOut.ifBlank { "default" }}")
        val standbyOut = execShell("am get-standby-bucket $sanitized")
        sb.appendLine("Standby bucket: ${standbyOut.ifBlank { "unknown" }}")
        return sb.toString().trim()
    }

    override fun getDeviceAudioSummary(): String {
        val proAudio = execShell("pm has-feature android.hardware.audio.pro")
        val lowLatency = execShell("pm has-feature android.hardware.audio.low_latency")
        val audioHal = execShell("getprop ro.hardware.audio")
        return buildString {
            appendLine("Audio Low Latency Feature: ${lowLatency.ifBlank { "unknown" }}")
            appendLine("Pro Audio Feature: ${proAudio.ifBlank { "unknown" }}")
            appendLine("Audio HAL Prop: ${audioHal.ifBlank { "standard" }}")
            appendLine("Note: Android AudioPolicy isolates per-app AudioRecord streams; non-root UID 2000 inspects routing state without cross-app mic hijacking.")
        }.trim()
    }

    private fun execShell(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val out = StringBuilder()
            var lineCount = 0
            while (lineCount < 40) {
                val line = reader.readLine() ?: break
                out.appendLine(line)
                lineCount++
            }
            process.waitFor()
            out.toString().trim()
        } catch (e: Exception) {
            "Shell command failed: ${e.message}"
        }
    }
}
