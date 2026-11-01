package org.opened.app.core.tts

import android.content.Context
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
    val errorMessage: String? = null
)

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var pendingText: String? = null

    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.update { it.copy(playbackState = TtsPlaybackState.PLAYING) }
                    }
                    override fun onDone(utteranceId: String?) {
                        _state.update { it.copy(playbackState = TtsPlaybackState.IDLE) }
                    }
                    @Deprecated("Deprecated in API")
                    override fun onError(utteranceId: String?) {
                        _state.update { it.copy(playbackState = TtsPlaybackState.ERROR, errorMessage = "TTS error") }
                    }
                })
                _state.update { it.copy(isEngineReady = true) }
                pendingText?.let { speak(it) }
                pendingText = null
            } else {
                _state.update { it.copy(errorMessage = "TTS engine failed to initialize") }
            }
        }
    }

    fun speak(rawMarkdown: String) {
        val cleanText = stripMarkdownForTts(rawMarkdown)
        val engine = tts
        if (engine == null || !_state.value.isEngineReady) {
            pendingText = cleanText
            initialize()
            return
        }
        engine.setSpeechRate(_state.value.speedRate)
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "openedTts")
        _state.update { it.copy(playbackState = TtsPlaybackState.PLAYING) }
    }

    fun pause() {
        tts?.stop()
        _state.update { it.copy(playbackState = TtsPlaybackState.PAUSED) }
    }

    fun resume(rawMarkdown: String) {
        // TTS doesn't support true resume; restart from beginning
        speak(rawMarkdown)
    }

    fun stop() {
        tts?.stop()
        _state.update { it.copy(playbackState = TtsPlaybackState.IDLE) }
    }

    fun setSpeed(rate: Float) {
        _state.update { it.copy(speedRate = rate) }
        tts?.setSpeechRate(rate)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _state.update { TtsState() }
    }

    private fun stripMarkdownForTts(text: String): String {
        return text
            // Strip LaTeX display math blocks $$...$$
            .replace(Regex("""\$\$[\s\S]*?\$\$"""), " formula. ")
            // Strip inline LaTeX $...$
            .replace(Regex("""\$[^$]+\$"""), " formula ")
            // Strip code blocks ```...```
            .replace(Regex("""```[\s\S]*?```"""), " code block. ")
            // Strip inline code `...`
            .replace(Regex("""`[^`]+`""")) { it.value.trim('`') }
            // Strip [[WikiLinks]]
            .replace(Regex("""\[\[([^\]]+)\]\]""")) { it.groupValues[1] }
            // Strip markdown headers
            .replace(Regex("""^#{1,6}\s+""", RegexOption.MULTILINE), "")
            // Strip bold/italic
            .replace(Regex("""\*\*([^*]+)\*\*""")) { it.groupValues[1] }
            .replace(Regex("""\*([^*]+)\*""")) { it.groupValues[1] }
            .replace(Regex("""__([^_]+)__""")) { it.groupValues[1] }
            // Strip blockquote markers
            .replace(Regex("""^>\s*""", RegexOption.MULTILINE), "")
            // Strip callout markers like [!NOTE]
            .replace(Regex("""\[![A-Z]+\]\s?"""), "")
            // Strip markdown links [text](url)
            .replace(Regex("""\[([^\]]+)\]\([^)]+\)""")) { it.groupValues[1] }
            // Collapse excess whitespace
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
