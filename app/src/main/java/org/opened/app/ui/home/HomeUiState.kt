package org.opened.app.ui.home

import androidx.compose.runtime.Immutable
import org.opened.app.core.database.model.SearchResultItem
import org.opened.app.core.database.model.StudyPathWithModules

@Immutable
data class HomeUiState(
    val studyPaths: List<StudyPathWithModules> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchResultItem> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val hasApiKey: Boolean = true,
    val activeProviderName: String = "AI",
    val dueFlashcardCount: Int = 0,
    val errorMessage: String? = null
)
