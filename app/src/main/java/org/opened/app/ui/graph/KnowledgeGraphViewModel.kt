package org.opened.app.ui.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opened.app.core.database.model.ChapterLinkNode
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Inject

data class GraphUiState(
    val nodes: List<ChapterLinkNode> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class KnowledgeGraphViewModel @Inject constructor(
    private val repository: OpenEDRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GraphUiState())
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    init {
        loadGraph()
    }

    fun loadGraph() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val nodes = repository.getAllChapterLinkNodes()
            _uiState.update { it.copy(nodes = nodes, isLoading = false) }
        }
    }
}
