package org.opened.app.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.database.entity.FlashcardEntity
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

data class FlashcardReviewUiState(
    val dueCards: List<FlashcardEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isLoading: Boolean = true,
    val isSessionComplete: Boolean = false,
    val totalReviewed: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class FlashcardReviewViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    private val aiEngine: AiLearningEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashcardReviewUiState())
    val uiState: StateFlow<FlashcardReviewUiState> = _uiState.asStateFlow()

    init {
        loadDueCards()
    }

    private fun loadDueCards() {
        viewModelScope.launch {
            repository.getDueFlashcardsFlow(System.currentTimeMillis()).collect { cards ->
                _uiState.update {
                    it.copy(
                        dueCards = cards,
                        isLoading = false,
                        isSessionComplete = cards.isEmpty() && it.totalReviewed > 0
                    )
                }
            }
        }
    }

    fun flipCard() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    /** quality: 0=Again, 1=Hard, 2=Good, 3=Easy */
    fun rateCard(quality: Int) {
        val state = _uiState.value
        val card = state.dueCards.getOrNull(state.currentIndex) ?: return

        viewModelScope.launch {
            aiEngine.reviewFlashcard(card, quality)
            // After rating, move to next card in the current session queue
            _uiState.update { s ->
                val nextIndex = s.currentIndex + 1
                val sessionComplete = nextIndex >= s.dueCards.size
                s.copy(
                    currentIndex = if (sessionComplete) s.currentIndex else nextIndex,
                    isFlipped = false,
                    totalReviewed = s.totalReviewed + 1,
                    isSessionComplete = sessionComplete
                )
            }
        }
    }

    fun restartSession() {
        _uiState.update { it.copy(currentIndex = 0, isFlipped = false, isSessionComplete = false, totalReviewed = 0) }
        loadDueCards()
    }
}
