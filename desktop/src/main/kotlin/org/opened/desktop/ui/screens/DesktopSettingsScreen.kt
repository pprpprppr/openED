package org.opened.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opened.app.core.ai.AiProvider
import org.opened.app.core.ai.AiProviderModels
import org.opened.app.core.ai.TutorPersonaCategory
import org.opened.app.core.ai.TutorPersonaRegistry
import org.opened.app.core.ai.provider.ModelSelectionMode
import org.opened.app.core.datastore.UserPreferences
import org.opened.app.ui.platform.LocalPlatformBackupSection
import org.opened.app.ui.platform.openEdViewModel
import org.opened.app.ui.settings.SettingsUiState
import org.opened.app.ui.settings.SettingsViewModel
import org.opened.desktop.DesktopThemeMode
import org.opened.desktop.ui.components.DesktopCard
import org.opened.desktop.ui.components.DesktopPageHeader
import org.opened.desktop.ui.components.DesktopPillBadge
import org.opened.desktop.ui.theme.LocalDesktopTokens

private enum class DesktopSettingsCategory(val label: String, val icon: ImageVector) {
    APPEARANCE("Appearance & Themes", Icons.Default.Palette),
    AI_ENGINE("AI Engine & Keys", Icons.Default.Key),
    PERSONA("Tutor Persona & Prompts", Icons.Default.Tune),
    ACCESSIBILITY("Language & Accessibility", Icons.Default.Language),
    SYNC_BACKUP("Sync, Backup & Export", Icons.Default.CloudSync)
}

@Composable
fun DesktopSettingsScreen(
    currentThemeMode: DesktopThemeMode,
    onSelectThemeMode: (DesktopThemeMode) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = openEdViewModel()
) {
    val tokens = LocalDesktopTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCategory by remember { mutableStateOf(DesktopSettingsCategory.APPEARANCE) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.statusMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        containerColor = tokens.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(tokens.canvas)
        ) {
            DesktopPageHeader(
                title = "Desktop Settings, Themes & Backup",
                subtitle = "Configure appearance, AI engine keys, custom tutor prompts, localization & local/cloud sync",
                onBack = onNavigateBack
            )

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Left Category List
                Column(
                    modifier = Modifier
                        .width(236.dp)
                        .fillMaxHeight()
                        .background(tokens.sidebar)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DesktopSettingsCategory.entries.forEach { cat ->
                        val active = selectedCategory == cat
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedCategory = cat },
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) tokens.accentSoft else Color.Transparent,
                            border = if (active) BorderStroke(1.dp, tokens.accent.copy(alpha = 0.45f)) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = if (active) tokens.accent else tokens.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = cat.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) tokens.accent else tokens.textPrimary
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(tokens.border))

                // Right Category Detail Stage
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        when (selectedCategory) {
                            DesktopSettingsCategory.APPEARANCE -> {
                                DesktopAppearanceSettings(
                                    currentThemeMode = currentThemeMode,
                                    onSelectThemeMode = onSelectThemeMode,
                                    uiState = uiState,
                                    onHighContrastChange = viewModel::setHighContrastEnabled,
                                    onReducedMotionChange = viewModel::setReducedMotionEnabled
                                )
                            }
                            DesktopSettingsCategory.AI_ENGINE -> {
                                DesktopAiEngineSettings(uiState = uiState, viewModel = viewModel)
                            }
                            DesktopSettingsCategory.PERSONA -> {
                                DesktopPersonaSettings(uiState = uiState, viewModel = viewModel)
                            }
                            DesktopSettingsCategory.ACCESSIBILITY -> {
                                DesktopLocalizationAccessibilitySettings(uiState = uiState, viewModel = viewModel)
                            }
                            DesktopSettingsCategory.SYNC_BACKUP -> {
                                LocalPlatformBackupSection.current(uiState, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showGeneratePromptDialog) {
        DesktopGeneratePromptDialog(
            uiState = uiState,
            onDraftChange = viewModel::setPromptIdeaDraft,
            onGenerate = viewModel::generateSystemInstructionPrompt,
            onApply = viewModel::applyGeneratedPrompt,
            onDismiss = viewModel::closeGeneratePromptDialog
        )
    }
}

@Composable
private fun DesktopAppearanceSettings(
    currentThemeMode: DesktopThemeMode,
    onSelectThemeMode: (DesktopThemeMode) -> Unit,
    uiState: SettingsUiState,
    onHighContrastChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit
) {
    val tokens = LocalDesktopTokens.current
    DesktopCard(contentPadding = 22.dp) {
        Text(
            text = "PC Theme Selection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose between Modern Dark, Modern White, or openED's original Classic Studio theme. Only applies to the PC desktop app.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DesktopThemeMode.entries.forEach { mode ->
                val isSelected = currentThemeMode == mode
                val previewBg = when (mode) {
                    DesktopThemeMode.MODERN_DARK -> Color(0xFF0A0D14)
                    DesktopThemeMode.MODERN_WHITE -> Color(0xFFFFFFFF)
                    DesktopThemeMode.EXISTING -> Color(0xFF0B0F19)
                }
                val previewSidebar = when (mode) {
                    DesktopThemeMode.MODERN_DARK -> Color(0xFF0F131D)
                    DesktopThemeMode.MODERN_WHITE -> Color(0xFFF8FAFC)
                    DesktopThemeMode.EXISTING -> Color(0xFF111827)
                }
                val previewAccent = when (mode) {
                    DesktopThemeMode.MODERN_DARK -> Color(0xFF6366F1)
                    DesktopThemeMode.MODERN_WHITE -> Color(0xFF4F46E5)
                    DesktopThemeMode.EXISTING -> Color(0xFF06B6D4)
                }
                val previewIcon = when (mode) {
                    DesktopThemeMode.MODERN_DARK -> Icons.Default.DarkMode
                    DesktopThemeMode.MODERN_WHITE -> Icons.Default.LightMode
                    DesktopThemeMode.EXISTING -> Icons.Default.AutoAwesome
                }

                DesktopCard(
                    onClick = { onSelectThemeMode(mode) },
                    modifier = Modifier.weight(1f),
                    borderColor = if (isSelected) tokens.accent else tokens.border,
                    contentPadding = 14.dp
                ) {
                    // Mini Desktop Swatch Preview
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = previewBg,
                        border = BorderStroke(1.dp, tokens.border)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.width(34.dp).fillMaxHeight().background(previewSidebar))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(previewAccent)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(previewIcon, null, tint = if (isSelected) tokens.accent else tokens.textMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(mode.label, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, "Selected", tint = tokens.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(mode.subtitle, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                }
            }
        }
    }

    DesktopCard(contentPadding = 22.dp) {
        Text("Visual Comfort & Contrast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
        Spacer(modifier = Modifier.height(14.dp))

        DesktopSettingSwitchRow(
            title = "High-Contrast Palette (WCAG AAA)",
            subtitle = "Maximizes contrast ratios and border definitions across all PC themes.",
            checked = uiState.highContrastEnabled,
            onCheckedChange = onHighContrastChange
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = tokens.border)
        Spacer(modifier = Modifier.height(12.dp))
        DesktopSettingSwitchRow(
            title = "Reduced Motion",
            subtitle = "Minimizes card flips and animated transitions.",
            checked = uiState.reducedMotionEnabled,
            onCheckedChange = onReducedMotionChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopAiEngineSettings(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val tokens = LocalDesktopTokens.current
    var passwordVisible by remember { mutableStateOf(false) }
    var modelFilterQuery by remember { mutableStateOf("") }

    DesktopCard(contentPadding = 22.dp) {
        Text("AI Provider & Local Vault Keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "API keys are stored strictly on this PC in an AES-256 GCM encrypted local vault (~/.local/share/openED or %APPDATA%\\openED).",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
        Spacer(modifier = Modifier.height(14.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AiProvider.entries.forEachIndexed { index, provider ->
                SegmentedButton(
                    selected = uiState.selectedProvider == provider,
                    onClick = { viewModel.selectProvider(provider) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AiProvider.entries.size)
                ) {
                    Text(
                        when (provider) {
                            AiProvider.GEMINI -> "Gemini"
                            AiProvider.CLAUDE -> "Claude"
                            AiProvider.OPENAI -> "OpenAI"
                            AiProvider.CUSTOM -> "Custom"
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        val endpoint = if (uiState.selectedProvider == AiProvider.CUSTOM) {
            uiState.customBaseUrl.ifBlank { uiState.selectedProvider.defaultBaseUrl }
        } else uiState.selectedProvider.defaultBaseUrl
        DesktopPillBadge(text = "Endpoint: $endpoint")

        if (uiState.selectedProvider == AiProvider.CUSTOM) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "OpenRouter" to "https://openrouter.ai/api/v1",
                    "Groq" to "https://api.groq.com/openai/v1",
                    "Local Ollama" to "http://localhost:11434/v1"
                ).forEach { (lbl, url) ->
                    FilterChip(selected = uiState.customBaseUrl == url, onClick = { viewModel.selectCustomPreset(url) }, label = { Text(lbl) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.customBaseUrl,
                    onValueChange = viewModel::setCustomBaseUrlInput,
                    label = { Text("Custom API Base URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = viewModel::saveCustomBaseUrl, shape = RoundedCornerShape(9.dp)) {
                    Text("Save URL")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = viewModel::setApiKeyInput,
            label = { Text("${uiState.selectedProvider.displayName} API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle key")
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = viewModel::clearApiKey,
                enabled = uiState.hasApiKey || uiState.apiKey.isNotBlank(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.danger),
                shape = RoundedCornerShape(9.dp)
            ) { Text("Clear Key") }
            Button(
                onClick = viewModel::saveApiKey,
                enabled = uiState.apiKey.isNotBlank(),
                shape = RoundedCornerShape(9.dp)
            ) { Text("Save Key") }
            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = !uiState.isTestingConnection,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Testing...")
                } else {
                    Text("Test Connection")
                }
            }
        }
    }

    // Active Model Card
    DesktopCard(contentPadding = 22.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, null, tint = tokens.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Active Model & Strategy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            }
            FilledTonalButton(onClick = viewModel::syncLiveModels, enabled = !uiState.isSyncingModels, shape = RoundedCornerShape(9.dp)) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (uiState.isSyncingModels) "Syncing..." else "Sync API Models")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ModelSelectionMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.selectionMode == mode,
                    onClick = { viewModel.selectSelectionMode(mode) },
                    label = { Text(mode.displayName) }
                )
            }
        }
        Text(uiState.selectionMode.description, style = MaterialTheme.typography.bodySmall, color = tokens.accent)

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = modelFilterQuery,
            onValueChange = { modelFilterQuery = it },
            placeholder = { Text("Filter models (e.g. flash, claude, gpt, llama)...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        val filteredModels = remember(uiState.availableModels, modelFilterQuery) {
            if (modelFilterQuery.isBlank()) uiState.availableModels
            else uiState.availableModels.filter {
                it.contains(modelFilterQuery.trim(), ignoreCase = true) ||
                    AiProviderModels.getModelDescription(it).contains(modelFilterQuery.trim(), ignoreCase = true)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 260.dp),
            shape = RoundedCornerShape(10.dp),
            color = tokens.cardSubtle,
            border = BorderStroke(1.dp, tokens.border)
        ) {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(filteredModels, key = { it }) { model ->
                    val selected = uiState.selectedModel == model
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.selectModel(model) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { viewModel.selectModel(model) })
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = tokens.textPrimary)
                            val desc = AiProviderModels.getModelDescription(model)
                            if (desc.isNotEmpty()) {
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.customModelInput,
                onValueChange = viewModel::setCustomModelInput,
                label = { Text("Manual Custom Model ID") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = viewModel::applyCustomModel,
                enabled = uiState.customModelInput.isNotBlank(),
                shape = RoundedCornerShape(9.dp)
            ) { Text("Set Model") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopPersonaSettings(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val tokens = LocalDesktopTokens.current
    var selectedCategory by remember { mutableStateOf(TutorPersonaCategory.ALL) }
    val templates = listOf(
        "3 Takeaways" to "Always conclude each lesson with 3 concise summary takeaways and bulleted key points.",
        "Self-Test Quiz" to "At the end of each chapter, provide 3 multiple-choice self-test questions with detailed answer explanations.",
        "Case Studies" to "Ground every conceptual theory in an authentic real-world case study or empirical experiment.",
        "Socratic Inquirer" to "Include thought-provoking Socratic reflection questions throughout to stimulate critical thinking.",
        "Step-by-Step Proofs" to "Derive equations and workflows step-by-step with clear theoretical justification before every formula.",
        "Analogy-Driven" to "Explain abstract concepts using relatable everyday analogies before technical formalisms."
    )

    DesktopCard(contentPadding = 22.dp) {
        Text("Subject Tutor Personas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TutorPersonaCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text("${cat.icon} ${cat.displayName}") }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorPersonaRegistry.getByCategory(selectedCategory).forEach { persona ->
                val isApplied = uiState.customSystemInstructions.contains(persona.instruction.take(40), ignoreCase = true)
                FilterChip(
                    selected = isApplied,
                    onClick = { viewModel.applySystemInstructionPreset(persona.instruction) },
                    label = { Text("${persona.icon} ${persona.title}") }
                )
            }
        }
    }

    DesktopCard(contentPadding = 22.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Custom System Instructions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            if (uiState.customSystemInstructions.isNotBlank()) {
                DesktopPillBadge(text = "Active")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            templates.forEach { (label, snippet) ->
                val selected = uiState.customSystemInstructions.contains(snippet, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleCustomTemplate(snippet) },
                    label = { Text(label) },
                    leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) } } else null
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.customSystemInstructions,
            onValueChange = viewModel::setCustomSystemInstructions,
            label = { Text("Custom Instructions") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalButton(onClick = { viewModel.openGeneratePromptDialog() }, shape = RoundedCornerShape(9.dp)) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate with AI")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.customSystemInstructions.isNotBlank()) {
                    OutlinedButton(onClick = viewModel::clearCustomSystemInstructions, shape = RoundedCornerShape(9.dp)) { Text("Clear") }
                }
                OutlinedButton(onClick = viewModel::resetSystemInstructions, shape = RoundedCornerShape(9.dp)) { Text("Reset Default") }
                Button(onClick = viewModel::saveCustomSystemInstructions, shape = RoundedCornerShape(9.dp)) { Text("Save Instructions") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopLocalizationAccessibilitySettings(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val tokens = LocalDesktopTokens.current
    DesktopCard(contentPadding = 22.dp) {
        Text("Teaching Language & Localization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UserPreferences.SUPPORTED_LANGUAGES.forEach { lang ->
                FilterChip(
                    selected = uiState.learningLanguage.equals(lang, ignoreCase = true),
                    onClick = { viewModel.setLearningLanguage(lang) },
                    label = { Text(lang) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = tokens.border)
        Spacer(modifier = Modifier.height(14.dp))
        DesktopSettingSwitchRow(
            title = "Bilingual Technical Terms",
            subtitle = "Show native translation and English technical terminology side-by-side.",
            checked = uiState.bilingualTerminologyEnabled,
            onCheckedChange = viewModel::setBilingualTerminologyEnabled
        )
        Spacer(modifier = Modifier.height(12.dp))
        DesktopSettingSwitchRow(
            title = "High-Contrast Palette (WCAG AAA)",
            subtitle = "Maximizes contrast ratios and border definitions.",
            checked = uiState.highContrastEnabled,
            onCheckedChange = viewModel::setHighContrastEnabled
        )
        Spacer(modifier = Modifier.height(12.dp))
        DesktopSettingSwitchRow(
            title = "Reduced Motion",
            subtitle = "Minimizes dynamic animations across flashcards and screens.",
            checked = uiState.reducedMotionEnabled,
            onCheckedChange = viewModel::setReducedMotionEnabled
        )
    }
}

@Composable
private fun DesktopSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = LocalDesktopTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DesktopGeneratePromptDialog(
    uiState: SettingsUiState,
    onDraftChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prompt Architect", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(460.dp)) {
                Text("Describe your learning goals and teaching preferences in plain English:", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = uiState.promptIdeaDraft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("e.g. Concise first-principles explanations with Socratic checks...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.promptGenerationError != null) {
                    Text(uiState.promptGenerationError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                uiState.generatedPromptPreview?.let { preview ->
                    DesktopCard(subtle = true, contentPadding = 12.dp) {
                        Text(preview, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onGenerate, enabled = !uiState.isGeneratingPrompt) {
                    Text(if (uiState.isGeneratingPrompt) "Generating..." else "Generate")
                }
                if (!uiState.generatedPromptPreview.isNullOrBlank()) {
                    Button(onClick = onApply) { Text("Apply Prompt") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
