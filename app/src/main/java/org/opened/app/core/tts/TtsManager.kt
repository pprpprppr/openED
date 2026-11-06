package org.opened.app.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class TtsPlaybackState { IDLE, PLAYING, PAUSED, ERROR }

data class TtsState(
    val playbackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val speedRate: Float = 1.0f,
    val isEngineReady: Boolean = false,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val errorMessage: String? = null
)

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var activeChunks: List<String> = emptyList()
    private var pendingChunks: List<String> = emptyList()
    private var pendingStartIndex: Int = 0

    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(audioAttributes)

                    val defaultLocale = Locale.getDefault()
                    val langResult = engine.setLanguage(defaultLocale)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.language = Locale.US
                    }
                    engine.setSpeechRate(_state.value.speedRate)

                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            val idx = utteranceId?.removePrefix("chunk_")?.toIntOrNull()
                            _state.update { current ->
                                current.copy(
                                    playbackState = TtsPlaybackState.PLAYING,
                                    currentChunkIndex = idx ?: current.currentChunkIndex,
                                    errorMessage = null
                                )
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            val idx = utteranceId?.removePrefix("chunk_")?.toIntOrNull()
                            val total = _state.value.totalChunks
                            if (idx != null && total > 0 && idx >= total - 1) {
                                _state.update {
                                    it.copy(
                                        playbackState = TtsPlaybackState.IDLE,
                                        currentChunkIndex = 0
                                    )
                                }
                            }
                        }

                        @Deprecated("Deprecated in API 21")
                        override fun onError(utteranceId: String?) {
                            _state.update {
                                it.copy(
                                    playbackState = TtsPlaybackState.ERROR,
                                    errorMessage = "Audio playback encountered an error."
                                )
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _state.update {
                                it.copy(
                                    playbackState = TtsPlaybackState.ERROR,
                                    errorMessage = "Audio playback encountered an error (code $errorCode)."
                                )
                            }
                        }
                    })
                }

                _state.update { it.copy(isEngineReady = true, errorMessage = null) }

                if (pendingChunks.isNotEmpty()) {
                    val toSpeak = pendingChunks
                    val startIdx = pendingStartIndex
                    pendingChunks = emptyList()
                    pendingStartIndex = 0
                    speakChunks(toSpeak, startIdx)
                }
            } else {
                _state.update {
                    it.copy(
                        playbackState = TtsPlaybackState.ERROR,
                        errorMessage = "⚠️ System Text-to-Speech engine not available on this device. Check Android Accessibility -> Text-to-speech output."
                    )
                }
            }
        }
    }

    /**
     * Cleans raw Markdown and initiates chunked spoken audio playback.
     */
    fun speak(rawMarkdown: String) {
        val clean = MarkdownStripper.cleanMarkdownSync(rawMarkdown)
        val chunks = MarkdownStripper.chunkForSpeech(clean, maxChunkChars = 1800)
        if (chunks.isEmpty()) {
            _state.update { it.copy(playbackState = TtsPlaybackState.IDLE) }
            return
        }
        speakChunks(chunks, startIndex = 0)
    }

    /**
     * Speaks pre-chunked speech segments, queueing them sequentially.
     */
    fun speakChunks(chunks: List<String>, startIndex: Int = 0) {
        if (chunks.isEmpty()) return
        activeChunks = chunks
        val safeIndex = startIndex.coerceIn(0, chunks.size - 1)

        val engine = tts
        if (engine == null || !_state.value.isEngineReady) {
            pendingChunks = chunks
            pendingStartIndex = safeIndex
            initialize()
            return
        }

        _state.update {
            it.copy(
                playbackState = TtsPlaybackState.PLAYING,
                currentChunkIndex = safeIndex,
                totalChunks = chunks.size,
                errorMessage = null
            )
        }

        engine.setSpeechRate(_state.value.speedRate)

        // Queue first chunk with QUEUE_FLUSH to stop any prior speech
        engine.speak(chunks[safeIndex], TextToSpeech.QUEUE_FLUSH, null, "chunk_$safeIndex")

        // Queue all subsequent chunks with QUEUE_ADD for uninterrupted streaming playback
        for (i in (safeIndex + 1) until chunks.size) {
            engine.speak(chunks[i], TextToSpeech.QUEUE_ADD, null, "chunk_$i")
        }
    }

    fun pause() {
        tts?.stop()
        _state.update { it.copy(playbackState = TtsPlaybackState.PAUSED) }
    }

    fun resume() {
        if (activeChunks.isNotEmpty()) {
            speakChunks(activeChunks, startIndex = _state.value.currentChunkIndex)
        }
    }

    fun stop() {
        tts?.stop()
        _state.update {
            it.copy(
                playbackState = TtsPlaybackState.IDLE,
                currentChunkIndex = 0
            )
        }
    }

    fun setSpeed(rate: Float) {
        _state.update { it.copy(speedRate = rate) }
        tts?.setSpeechRate(rate)
        if (_state.value.playbackState == TtsPlaybackState.PLAYING && activeChunks.isNotEmpty()) {
            speakChunks(activeChunks, startIndex = _state.value.currentChunkIndex)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        activeChunks = emptyList()
        pendingChunks = emptyList()
        _state.update { TtsState() }
    }
}
