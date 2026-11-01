package org.opened.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.security.SecureApiKeyStorage
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: OpenEDRepository,
    private val apiKeyStorage: SecureApiKeyStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        checkApiKey()
        loadStudyPaths()
        observeDueFlashcardCount()
    }

    fun checkApiKey() {
        val hasKey = apiKeyStorage.hasApiKey()
        _uiState.update { it.copy(hasApiKey = hasKey) }
    }

    private fun loadStudyPaths() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getAllStudyPathsFlow().collectLatest { paths ->
                _uiState.update { it.copy(studyPaths = paths, isLoading = false) }
            }
        }
    }

    private fun observeDueFlashcardCount() {
        viewModelScope.launch {
            repository.getDueFlashcardCount().collectLatest { count ->
                _uiState.update { it.copy(dueFlashcardCount = count) }
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

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(250)
            val results = repository.search(query.trim())
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun deleteStudyPath(pathId: String) {
        viewModelScope.launch {
            repository.deleteStudyPath(pathId)
        }
    }
}

