package org.opened.app.core.tts

import kotlinx.coroutines.flow.StateFlow

enum class TtsPlaybackState { IDLE, PLAYING, PAUSED, ERROR }

data class TtsState(
    val playbackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val speedRate: Float = 1.0f,
    val isEngineReady: Boolean = false,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val errorMessage: String? = null
)

interface TtsController {
    val state: StateFlow<TtsState>
    fun initialize()
    fun speak(rawMarkdown: String)
    fun speakChunks(chunks: List<String>, startIndex: Int = 0)
    fun pause()
    fun resume()
    fun stop()
    fun setSpeed(rate: Float)
    fun shutdown()
}
