package org.opened.app.learning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opened.app.learning.data.entity.ContentBlock
import org.opened.app.learning.model.BlockType
import org.opened.app.learning.model.ChapterStatus
import org.opened.app.learning.model.ChapterWithContentBlocks
import org.opened.app.learning.ui.blocks.AiPromptBlock
import org.opened.app.learning.ui.blocks.CodeBlock
import org.opened.app.learning.ui.blocks.ImageBlock
import org.opened.app.learning.ui.blocks.TextBlock

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChapterEditorView(
    chapterWithBlocks: ChapterWithContentBlocks?,
    blocks: List<ContentBlock>,
    isCompactScreen: Boolean,
    onBackToList: () -> Unit,
    onBlockMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onBlockDrop: () -> Unit,
    onUpdateBlock: (ContentBlock) -> Unit,
    onDeleteBlock: (ContentBlock) -> Unit,
    onAddBlock: (BlockType) -> Unit,
    onGenerateAi: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chapterWithBlocks == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select a chapter from the outline to view and edit its content.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val chapter = chapterWithBlocks.chapter
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableState(
        listState = listState,
        onMove = onBlockMove,
        onDragEnd = onBlockDrop
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Chapter Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Phone back button
                if (isCompactScreen) {
                    IconButton(onClick = onBackToList) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back to chapters")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${blocks.size} Blocks · Long-press block to reorder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // AI Generation Button
            if (chapter.status == ChapterStatus.Generating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Synthesizing...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = { onGenerateAi(chapter.chapterId) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Generate", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))

        // Reorderable LazyColumn for Content Blocks
        if (blocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No content blocks yet. Add text, code, or prompts using the toolbar below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                itemsIndexed(
                    items = blocks,
                    key = { _, block -> block.blockId },
                    contentType = { _, block -> block.blockType }
                ) { index, block ->
                    val itemModifier = Modifier.reorderableItem(reorderableState, index)

                    when (block.blockType) {
                        BlockType.TEXT -> {
                            TextBlock(
                                block = block,
                                onContentChange = { updated ->
                                    onUpdateBlock(block.copy(content = updated))
                                },
                                onDelete = { onDeleteBlock(block) },
                                modifier = itemModifier
                            )
                        }

                        BlockType.CODE -> {
                            CodeBlock(
                                block = block,
                                onContentChange = { updated ->
                                    onUpdateBlock(block.copy(content = updated))
                                },
                                onDelete = { onDeleteBlock(block) },
                                modifier = itemModifier
                            )
                        }

                        BlockType.IMAGE -> {
                            ImageBlock(
                                block = block,
                                onContentChange = { updated ->
                                    onUpdateBlock(block.copy(content = updated))
                                },
                                onDelete = { onDeleteBlock(block) },
                                modifier = itemModifier
                            )
                        }

                        BlockType.AI_PROMPT -> {
                            AiPromptBlock(
                                block = block,
                                onContentChange = { updated ->
                                    onUpdateBlock(block.copy(content = updated))
                                },
                                onGenerate = { onGenerateAi(chapter.chapterId) },
                                onDelete = { onDeleteBlock(block) },
                                modifier = itemModifier
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Content Block Addition Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { onAddBlock(BlockType.TEXT) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Text")
                }

                FilledTonalButton(
                    onClick = { onAddBlock(BlockType.CODE) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Code")
                }

                FilledTonalButton(
                    onClick = { onAddBlock(BlockType.IMAGE) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Image")
                }

                FilledTonalButton(
                    onClick = { onAddBlock(BlockType.AI_PROMPT) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ AI Prompt")
                }
            }
        }
    }
}
