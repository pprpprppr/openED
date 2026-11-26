package org.opened.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.opened.app.core.tts.MarkdownStripper
import org.opened.app.core.tts.TtsController
import org.opened.app.core.tts.TtsPlaybackState
import org.opened.app.core.tts.TtsState

/**
 * Cross-platform Desktop Text-to-Speech controller using native OS speech synthesis:
 * - Windows: PowerShell System.Speech.Synthesis.SpeechSynthesizer
 * - Linux: spd-say, espeak-ng, or espeak
 */
class DesktopTtsManager : TtsController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(TtsState())
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private var activeChunks: List<String> = emptyList()
    private var playbackJob: Job? = null
    @Volatile
    private var activeProcess: Process? = null
    private var linuxCommand: String? = null

    override fun initialize() {
        if (DesktopPaths.isWindows) {
            _state.update { it.copy(isEngineReady = true, errorMessage = null) }
            return
        }
        val detected = listOf("spd-say", "espeak-ng", "espeak").firstOrNull { cmd ->
            runCatching {
                ProcessBuilder("which", cmd).start().waitFor() == 0
            }.getOrDefault(false)
        }
        linuxCommand = detected
        if (detected != null) {
            _state.update { it.copy(isEngineReady = true, errorMessage = null) }
        } else {
            _state.update {
                it.copy(
                    isEngineReady = false,
                    playbackState = TtsPlaybackState.ERROR,
                    errorMessage = "No Linux TTS engine found. Install speech-dispatcher (spd-say) or espeak-ng for audio playback."
                )
            }
        }
    }

    override fun speak(rawMarkdown: String) {
        val clean = MarkdownStripper.cleanMarkdownSync(rawMarkdown)
        val chunks = MarkdownStripper.chunkForSpeech(clean, maxChunkChars = 1400)
        if (chunks.isEmpty()) {
            _state.update { it.copy(playbackState = TtsPlaybackState.IDLE) }
            return
        }
        speakChunks(chunks, startIndex = 0)
    }

    override fun speakChunks(chunks: List<String>, startIndex: Int) {
        if (chunks.isEmpty()) return
        if (!_state.value.isEngineReady) {
            initialize()
            if (!_state.value.isEngineReady) return
        }

        stopActiveProcessOnly()
        activeChunks = chunks
        val safeStart = startIndex.coerceIn(0, chunks.size - 1)

        playbackJob = scope.launch {
            _state.update {
                it.copy(
                    playbackState = TtsPlaybackState.PLAYING,
                    currentChunkIndex = safeStart,
                    totalChunks = chunks.size,
                    errorMessage = null
                )
            }

            for (idx in safeStart until chunks.size) {
                if (!isActive) break
                _state.update { it.copy(currentChunkIndex = idx) }
                val exit = runSpeechProcess(chunks[idx], _state.value.speedRate)
                if (!isActive || exit != 0) break
            }

            if (isActive) {
                _state.update {
                    it.copy(playbackState = TtsPlaybackState.IDLE, currentChunkIndex = 0)
                }
            }
        }
    }

    private fun runSpeechProcess(text: String, speedRate: Float): Int {
        return runCatching {
            val pb = if (DesktopPaths.isWindows) {
                val winRate = ((speedRate - 1.0f) * 5f).toInt().coerceIn(-5, 5)
                val escaped = text.replace("'", "''").replace("\n", " ")
                val psCmd = "Add-Type -AssemblyName System.Speech; \$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; \$s.Rate = $winRate; \$s.Speak('$escaped')"
                ProcessBuilder("powershell", "-NoProfile", "-Command", psCmd)
            } else {
                val cmd = linuxCommand ?: return -1
                when (cmd) {
                    "spd-say" -> {
                        val spdRate = ((speedRate - 1.0f) * 50f).toInt().coerceIn(-50, 50)
                        ProcessBuilder("spd-say", "--wait", "--rate", spdRate.toString(), text)
                    }
                    else -> {
                        val wpm = (165 * speedRate).toInt().coerceIn(80, 360)
                        ProcessBuilder(cmd, "-s", wpm.toString(), text)
                    }
                }
            }
            val proc = pb.start()
            activeProcess = proc
            proc.waitFor()
        }.getOrDefault(-1)
    }

    private fun stopActiveProcessOnly() {
        playbackJob?.cancel()
        playbackJob = null
        runCatching { activeProcess?.destroyForcibly() }
        activeProcess = null
    }

    override fun pause() {
        stopActiveProcessOnly()
        _state.update { it.copy(playbackState = TtsPlaybackState.PAUSED) }
    }

    override fun resume() {
        if (activeChunks.isNotEmpty()) {
            speakChunks(activeChunks, startIndex = _state.value.currentChunkIndex)
        }
    }

    override fun stop() {
        stopActiveProcessOnly()
        _state.update {
            it.copy(playbackState = TtsPlaybackState.IDLE, currentChunkIndex = 0)
        }
    }

    override fun setSpeed(rate: Float) {
        _state.update { it.copy(speedRate = rate) }
        if (_state.value.playbackState == TtsPlaybackState.PLAYING && activeChunks.isNotEmpty()) {
            speakChunks(activeChunks, startIndex = _state.value.currentChunkIndex)
        }
    }

    override fun shutdown() {
        stop()
        activeChunks = emptyList()
        _state.update { TtsState() }
    }
}
