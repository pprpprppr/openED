package org.opened.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opened.app.core.ai.RefineAction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InlineRefineBottomSheet(
    selectedSnippet: String,
    isRefining: Boolean,
    onDismiss: () -> Unit,
    onApplyRefinement: (String, RefineAction, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedAction by remember { mutableStateOf(RefineAction.SIMPLIFY) }
    var customPrompt by remember { mutableStateOf("") }
    var snippetText by remember { mutableStateOf(selectedSnippet) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Refine & Correct Passage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1-Tap Depth Level Switcher
            Text(
                text = "DEPTH LEVEL SWITCHER (ONE-TAP CONVERSION)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DepthPresetCard(
                    title = "🌱 Explain for Beginners (Step-by-Step + Examples)",
                    description = "Clear real-world analogies, zero obscure compiler/C internals, 3-4 code walkthroughs, visual diagram & pitfalls table.",
                    isEnabled = !isRefining,
                    onClick = {
                        onApplyRefinement(
                            "",
                            RefineAction.CUSTOM,
                            "Rewrite this entire chapter from zero assumptions for a beginner. Strictly forbid obscure compiler/C-struct internals. Start with a clear everyday real-world analogy. Include a simple ```diagram flowchart, 3 to 4 progressive well-commented code walkthroughs with inputs and outputs, and a common pitfalls section with a markdown table. Only use LaTeX math if it is a pure math topic."
                        )
                    }
                )

                DepthPresetCard(
                    title = "⚡ Practical & Hands-On (More Code & Real-World Use Cases)",
                    description = "Idiomatic patterns, realistic architectures, practical trade-offs, and production debugging scenarios.",
                    isEnabled = !isRefining,
                    onClick = {
                        onApplyRefinement(
                            "",
                            RefineAction.CUSTOM,
                            "Rewrite this chapter with a practical, hands-on focus. Include idiomatic patterns, real-world architecture, multiple concrete code examples solving realistic problems, and common production debugging scenarios."
                        )
                    }
                )

                DepthPresetCard(
                    title = "🔬 Deep Technical Dive",
                    description = "Under-the-hood systems internals, memory layout, algorithmic complexity proofs, and advanced scaling.",
                    isEnabled = !isRefining,
                    onClick = {
                        onApplyRefinement(
                            "",
                            RefineAction.CUSTOM,
                            "Rewrite this chapter as a deep technical dive. Explore under-the-hood systems architecture, memory layout, algorithmic complexity proofs, and advanced edge-case performance optimizations."
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // Passage to refine (editable snippet)
            Text(
                text = "SELECTED PASSAGE / SECTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = snippetText,
                onValueChange = { snippetText = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                placeholder = { Text("Paste or type passage from chapter...") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Selection Chips
            Text(
                text = "TRANSFORMATION ACTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RefineChip(
                    label = "Simplify",
                    icon = Icons.Default.Psychology,
                    isSelected = selectedAction == RefineAction.SIMPLIFY,
                    onClick = { selectedAction = RefineAction.SIMPLIFY }
                )

                RefineChip(
                    label = "Add Code/Math Example",
                    icon = Icons.Default.Code,
                    isSelected = selectedAction == RefineAction.ADD_CODE_MATH,
                    onClick = { selectedAction = RefineAction.ADD_CODE_MATH }
                )

                RefineChip(
                    label = "Fact-Check & Correct",
                    icon = Icons.Default.FactCheck,
                    isSelected = selectedAction == RefineAction.FACT_CHECK_CORRECT,
                    onClick = { selectedAction = RefineAction.FACT_CHECK_CORRECT }
                )

                RefineChip(
                    label = "Custom Prompt",
                    icon = Icons.Default.EditNote,
                    isSelected = selectedAction == RefineAction.CUSTOM,
                    onClick = { selectedAction = RefineAction.CUSTOM }
                )
            }

            if (selectedAction == RefineAction.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    label = { Text("Custom Refinement Instruction") },
                    placeholder = { Text("e.g. Rewrite with async/await coroutines in Kotlin...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Submit Button
            Button(
                onClick = {
                    onApplyRefinement(
                        snippetText,
                        selectedAction,
                        if (selectedAction == RefineAction.CUSTOM) customPrompt else null
                    )
                },
                enabled = !isRefining && snippetText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRefining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refining & Mutating in Place...")
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply & Update Chapter")
                }
            }
        }
    }
}

@Composable
private fun RefineChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DepthPresetCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
