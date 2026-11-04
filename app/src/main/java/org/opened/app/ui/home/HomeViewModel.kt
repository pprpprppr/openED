package org.opened.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.ai.formatUserFriendlyAiError
import org.opened.app.core.security.SecureApiKeyStorage
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    private val apiKeyStorage: SecureApiKeyStorage
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
        _uiState.update {
            it.copy(
                errorMessage = formatUserFriendlyAiError(throwable),
                isLoading = false,
                isSearching = false
            )
        }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        checkApiKey()
        loadStudyPaths()
        observeDueFlashcardCount()
    }

    fun checkApiKey() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                val hasKey = apiKeyStorage.hasApiKey()
                _uiState.update { it.copy(hasApiKey = hasKey) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to verify API key.") }
            }
        }
    }

    private fun loadStudyPaths() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.getAllStudyPathsFlow()
                    .catch { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load courses.") }
                    }
                    .collectLatest { paths ->
                        _uiState.update { it.copy(studyPaths = paths, isLoading = false) }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load courses.") }
            }
        }
    }

    private fun observeDueFlashcardCount() {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                repository.getDueFlashcardCount()
                    .catch { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _uiState.update { it.copy(errorMessage = e.localizedMessage) }
                    }
                    .collectLatest { count ->
                        _uiState.update { it.copy(dueFlashcardCount = count) }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isSearching = true) }
                delay(250)
                val results = repository.search(query.trim())
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, errorMessage = e.localizedMessage ?: "Search failed.") }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun deleteStudyPath(pathId: String) {
        viewModelScope.launch(exceptionHandler + Dispatchers.IO) {
            try {
                repository.deleteStudyPath(pathId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to delete course.") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
