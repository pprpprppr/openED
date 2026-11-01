package org.opened.app.learning.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State holder for drag-and-drop reordering inside a LazyColumn.
 */
class ReorderableState(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragEnd: () -> Unit
) {
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    var draggingItemOffset by mutableFloatStateOf(0f)
        private set

    private val currentDraggedItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex }

    fun onDragStart(index: Int) {
        draggedIndex = index
        draggingItemOffset = 0f
    }

    fun onDrag(dragAmount: Float) {
        val currentIndex = draggedIndex ?: return
        draggingItemOffset += dragAmount

        val currentItem = currentDraggedItem ?: return
        val currentItemTop = currentItem.offset.toFloat() + draggingItemOffset
        val currentItemBottom = currentItemTop + currentItem.size.toFloat()

        // Find collision with neighboring items
        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            if (item.index == currentIndex) return@firstOrNull false
            val itemMiddle = item.offset.toFloat() + item.size.toFloat() / 2f
            if (draggingItemOffset > 0) {
                // Moving down
                itemMiddle in currentItemTop..currentItemBottom && item.index > currentIndex
            } else {
                // Moving up
                itemMiddle in currentItemTop..currentItemBottom && item.index < currentIndex
            }
        }

        if (targetItem != null) {
            val targetIndex = targetItem.index
            onMove(currentIndex, targetIndex)
            draggedIndex = targetIndex
            draggingItemOffset += (currentItem.offset - targetItem.offset).toFloat()
        }
    }

    fun onDragStopped() {
        draggedIndex = null
        draggingItemOffset = 0f
        onDragEnd()
    }
}

@Composable
fun rememberReorderableState(
    listState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit
): ReorderableState {
    val scope = rememberCoroutineScope()
    return remember(listState) {
        ReorderableState(
            listState = listState,
            scope = scope,
            onMove = onMove,
            onDragEnd = onDragEnd
        )
    }
}

/**
 * Modifier applied to each draggable item in the reorderable list.
 */
fun Modifier.reorderableItem(
    state: ReorderableState,
    index: Int
): Modifier = this
    .pointerInput(state, index) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(index) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = { state.onDragStopped() },
            onDragCancel = { state.onDragStopped() }
        )
    }
    .graphicsLayer {
        val isDragging = state.draggedIndex == index
        if (isDragging) {
            translationY = state.draggingItemOffset
            shadowElevation = 12f
            scaleX = 1.02f
            scaleY = 1.02f
        } else {
            translationY = 0f
            shadowElevation = 0f
            scaleX = 1f
            scaleY = 1f
        }
    }
    .zIndex(if (state.draggedIndex == index) 10f else 0f)
