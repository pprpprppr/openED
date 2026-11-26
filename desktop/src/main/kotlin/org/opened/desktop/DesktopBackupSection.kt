package org.opened.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opened.app.ui.settings.RestoreConflictDialog
import org.opened.app.ui.settings.SettingsUiState
import org.opened.app.ui.settings.SettingsViewModel
import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun DesktopBackupAndExportSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Multi-Device Sync Architecture Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Multi-Device Sync Architecture",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Local SQLite database stays on this PC and syncs seamlessly with Android backups ('opened_backup_v1.zip') or WebDAV/Nextcloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Text(
                    text = "Active Sync Provider",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.syncProviderId != "WEBDAV",
                        onClick = { viewModel.setSyncProviderId("LOCAL") },
                        label = { Text("Local Archive", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                    FilterChip(
                        selected = uiState.syncProviderId == "WEBDAV",
                        onClick = { viewModel.setSyncProviderId("WEBDAV") },
                        label = { Text("WebDAV", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                }

                if (uiState.syncProviderId == "WEBDAV") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.webDavServerUrl,
                        onValueChange = { viewModel.setWebDavConfig(it, uiState.webDavUsername) },
                        label = { Text("WebDAV / Nextcloud Server URL") },
                        placeholder = { Text("https://nextcloud.example.com/remote.php/webdav") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.webDavUsername,
                        onValueChange = { viewModel.setWebDavConfig(uiState.webDavServerUrl, it) },
                        label = { Text("WebDAV Username (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.syncWithActiveProvider() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBusy,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Synchronize Now")
                }
            }
        }

        // Local File Backup & Restore Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PC Local Backup & Restore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Export or restore your full knowledge base as 'opened_backup_v1.zip' (100% cross-compatible between Windows, Linux, and Android). API keys are never included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            pickSaveFile("Save openED Backup", "opened_backup_v1.zip", "ZIP Archives", "zip") { file ->
                                viewModel.setLinkedBackupUri(file.absolutePath)
                                viewModel.backupToFile(file)
                            }
                        },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (uiState.isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save Backup (.zip)", maxLines = 1, softWrap = false)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            pickOpenFile("Select openED Backup (.zip)", "ZIP Archives", "zip") { file ->
                                pendingRestoreFile = file
                            }
                        },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Restore (.zip)", maxLines = 1, softWrap = false)
                    }
                }

                if (uiState.lastBackupTimestamp > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val sdf = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
                    Text(
                        text = "Last Backup: ${sdf.format(Date(uiState.lastBackupTimestamp))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Local Data Folder: ${viewModel.getAppDataDirectoryPath()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(DesktopPaths.dataDir)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Data Folder")
                }
            }
        }

        // Export Knowledge Base Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Export Knowledge Base",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                Text("Export as Obsidian Markdown Vault (.zip)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Exports all courses as folders of .md files with YAML frontmatter and [[WikiLinks]].", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        viewModel.exportObsidianVault { generatedFile ->
                            pickSaveFile("Save Obsidian Vault", "openED_obsidian_vault.zip", "ZIP Archives", "zip") { dest ->
                                generatedFile.copyTo(dest, overwrite = true)
                            }
                        }
                    },
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Obsidian Vault")
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))

                Text("Export Flashcards as Anki Deck (.tsv)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Exports all generated flashcards as a tab-separated file ready for Anki import.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        viewModel.exportAnkiCsv { generatedFile ->
                            pickSaveFile("Save Anki Deck", "openED_anki_flashcards.tsv", "TSV Files", "tsv") { dest ->
                                generatedFile.copyTo(dest, overwrite = true)
                            }
                        }
                    },
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Anki Flashcards")
                }
            }
        }
    }

    pendingRestoreFile?.let { file ->
        RestoreConflictDialog(
            onDismiss = { pendingRestoreFile = null },
            onConfirm = { resolution ->
                pendingRestoreFile = null
                viewModel.restoreFromFile(file, resolution)
            }
        )
    }
}

private fun pickSaveFile(
    dialogTitle: String,
    defaultFileName: String,
    filterDescription: String,
    extension: String,
    onSelected: (File) -> Unit
) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser(DesktopPaths.backupsDir).apply {
            this.dialogTitle = dialogTitle
            selectedFile = File(DesktopPaths.backupsDir, defaultFileName)
            fileFilter = FileNameExtensionFilter(filterDescription, extension)
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var target = chooser.selectedFile
            if (!target.name.endsWith(".$extension", ignoreCase = true)) {
                target = File(target.parentFile, "${target.name}.$extension")
            }
            onSelected(target)
        }
    }
}

private fun pickOpenFile(
    dialogTitle: String,
    filterDescription: String,
    extension: String,
    onSelected: (File) -> Unit
) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser(DesktopPaths.backupsDir).apply {
            this.dialogTitle = dialogTitle
            fileFilter = FileNameExtensionFilter(filterDescription, extension)
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            onSelected(chooser.selectedFile)
        }
    }
}
