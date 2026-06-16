package com.neomods.libeditor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neomods.libeditor.model.PatchEntry
import com.neomods.libeditor.viewmodel.LibEditorViewModel
import com.neomods.libeditor.viewmodel.StringEditEntry

@Composable
fun ModificationsTab(viewModel: LibEditorViewModel) {
    val patches by viewModel.patches.collectAsState()
    val stringEdits by viewModel.stringEdits.collectAsState()

    val totalItems = patches.size + stringEdits.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Modifications ($totalItems)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (totalItems > 0) {
                TextButton(onClick = {
                    viewModel.clearPatches()
                    viewModel.clearStringEdits()
                }) {
                    Text("Clear All")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (totalItems == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No modifications yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Add patches or queue string edits to see them here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (patches.isNotEmpty()) {
                    item {
                        Text(
                            text = "Patches (${patches.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(patches, key = { "patch_${it.id}" }) { patch ->
                        ModificationPatchCard(
                            patch = patch,
                            onToggle = { viewModel.togglePatch(patch.id) },
                            onDelete = { viewModel.deletePatch(patch.id) },
                            onEdit = { updated -> viewModel.updatePatch(updated.id, updated) }
                        )
                    }
                }

                if (stringEdits.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "String Edits (${stringEdits.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    items(stringEdits, key = { "edit_${it.id}" }) { edit ->
                        ModificationStringEditCard(
                            edit = edit,
                            onToggle = { viewModel.toggleStringEdit(edit.id) },
                            onDelete = { viewModel.deleteStringEdit(edit.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModificationPatchCard(
    patch: PatchEntry,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (PatchEntry) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (patch.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = patch.offset,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = patch.enabled,
                        onCheckedChange = { onToggle() }
                    )
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (patch.description.isNotBlank()) {
                Text(
                    text = patch.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Original (${patch.originalBytes.replace(" ", "").length / 2}B)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatHexDisplay(patch.originalBytes),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Replace (${patch.replacementBytes.replace(" ", "").length / 2}B)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatHexDisplay(patch.replacementBytes),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        var offset by remember { mutableStateOf(patch.offset) }
        var replacement by remember { mutableStateOf(patch.replacementBytes) }
        var description by remember { mutableStateOf(patch.description) }
        var readLength by remember { mutableStateOf("4") }

        val currentReadResult by viewModel.currentReadResult.collectAsState()

        LaunchedEffect(offset) {
            val parsed = viewModel.parseHexOffsetPublic(offset)
            if (parsed != null && parsed >= 0) {
                val len = readLength.toIntOrNull() ?: 4
                viewModel.readOffset(offset, len)
            }
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Patch") },
            text = {
                Column {
                    OutlinedTextField(
                        value = offset,
                        onValueChange = { offset = it },
                        label = { Text("Offset") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = readLength,
                        onValueChange = { readLength = it.filter { c -> c.isDigit() } },
                        label = { Text("Read Length (bytes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    currentReadResult?.let { (hex, bytes) ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Current bytes at offset (${bytes.size}B)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatHexDisplay(hex),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        label = { Text("Replacement Bytes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val readBytes = currentReadResult?.first ?: patch.originalBytes
                    val normalizedOriginal = readBytes.replace(" ", "").uppercase()
                    onEdit(
                        patch.copy(
                            offset = offset,
                            originalBytes = normalizedOriginal,
                            replacementBytes = replacement,
                            description = description
                        )
                    )
                    showEditDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ModificationStringEditCard(
    edit: StringEditEntry,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (edit.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "0x${edit.offset.toString(16).uppercase()}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${edit.originalLength} bytes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = edit.enabled,
                        onCheckedChange = { onToggle() }
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Original",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = edit.originalValue,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Replace",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = edit.replacement,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
