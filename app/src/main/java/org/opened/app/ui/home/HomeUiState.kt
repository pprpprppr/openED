package org.opened.app.ui.home

import org.opened.app.core.database.model.SearchResultItem
import org.opened.app.core.database.model.StudyPathWithModules

data class HomeUiState(
    val studyPaths: List<StudyPathWithModules> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchResultItem> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val hasApiKey: Boolean = true,
    val dueFlashcardCount: Int = 0
)
