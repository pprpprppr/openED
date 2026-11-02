package org.opened.app.ui.laboratory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opened.app.core.ai.AcademicDomain

enum class LabCategory(val label: String, val icon: String, val domain: AcademicDomain) {
    MATH("Math Plot", "📐", AcademicDomain.MATH_AND_PHYSICAL_SCIENCES),
    PHYSICS("Physics Sim", "🚀", AcademicDomain.MATH_AND_PHYSICAL_SCIENCES),
    ECONOMICS("Economics", "📈", AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE),
    BIOLOGY("Bio Pathway", "🧬", AcademicDomain.LIFE_SCIENCES_AND_MEDICINE),
    HISTORY("Timeline", "⏳", AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL),
    LOGIC("Argument Map", "⚖️", AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveLaboratorySheet(
    initialDomain: AcademicDomain = AcademicDomain.MATH_AND_PHYSICAL_SCIENCES,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultIndex = remember(initialDomain) {
        when (initialDomain) {
            AcademicDomain.MATH_AND_PHYSICAL_SCIENCES -> 0
            AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE -> 2
            AcademicDomain.LIFE_SCIENCES_AND_MEDICINE -> 3
            AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL -> 4
            AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES -> 5
            else -> 0
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(defaultIndex) }
    val categories = LabCategory.values()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Interactive Knowledge Laboratory",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Lab")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lab Category Tabs
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                divider = {}
            ) {
                categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = "${cat.icon} ${cat.label}",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Laboratory Canvas
            when (categories[selectedTabIndex]) {
                LabCategory.MATH -> InteractiveMathPlotCanvas()
                LabCategory.PHYSICS -> InteractivePhysicsSimulationCanvas()
                LabCategory.ECONOMICS -> InteractiveEconomicsMarketCanvas()
                LabCategory.BIOLOGY -> InteractiveBiologicalPathwayCanvas()
                LabCategory.HISTORY -> InteractiveHistoricalTimelineCanvas()
                LabCategory.LOGIC -> InteractiveLogicalArgumentMapCanvas()
            }
        }
    }
}
