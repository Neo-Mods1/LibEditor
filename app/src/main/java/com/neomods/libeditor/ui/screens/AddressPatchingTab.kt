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

@Composable
fun AddressPatchingTab(viewModel: LibEditorViewModel) {
    val patches by viewModel.patches.collectAsState()
    val currentReadResult by viewModel.currentReadResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var offsetInput by remember { mutableStateOf("") }
    var patchInput by remember { mutableStateOf("") }
    var descriptionInput by remember { mutableStateOf("") }
    var eightByteLock by remember { mutableStateOf(false) }

    var editingPatch by remember { mutableStateOf<PatchEntry?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    var showLargePatchWarning by remember { mutableStateOf(false) }
    var pendingPatchOffset by remember { mutableStateOf("") }
    var pendingPatchBytes by remember { mutableStateOf("") }
    var pendingPatchDesc by remember { mutableStateOf("") }

    val replacementByteCount = remember(patchInput) {
        val cleaned = patchInput.replace(" ", "").replace("0x", "", ignoreCase = true)
        if (cleaned.isNotEmpty() && cleaned.length % 2 == 0 && cleaned.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) {
            cleaned.length / 2
        } else {
            0
        }
    }

    fun doReadOffset(offset: String, length: Int) {
        viewModel.readOffset(offset, length)
    }

    fun doAddPatch(offset: String, replacement: String, desc: String) {
        viewModel.addPatch(offset, replacement, desc)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Address Patching",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = offsetInput,
            onValueChange = { offsetInput = it },
            label = { Text("Offset (e.g., 0x123456)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = patchInput,
            onValueChange = { patchInput = it },
            label = { Text("Replacement Bytes (e.g., 00 00 A0 E3)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = eightByteLock,
                    onCheckedChange = { eightByteLock = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "8-byte lock",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (replacementByteCount > 0) {
                Text(
                    text = "${replacementByteCount}B",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (eightByteLock && replacementByteCount > 8)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val readLen = if (eightByteLock) {
                    minOf(replacementByteCount, 8).coerceAtLeast(1)
                } else {
                    replacementByteCount.coerceAtLeast(1)
                }
                doReadOffset(offsetInput, readLen)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = offsetInput.isNotBlank() && replacementByteCount > 0 && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Read ${if (eightByteLock) minOf(replacementByteCount, 8) else replacementByteCount} bytes from offset")
        }

        currentReadResult?.let { (hex, bytes) ->
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Original (${bytes.size} bytes)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatHexDisplay(hex),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    val normalizedReplacement = patchInput.replace(" ", "").uppercase()
                    val replacementBytes = if (normalizedReplacement.isNotEmpty() && normalizedReplacement.length % 2 == 0) {
                        normalizedReplacement.chunked(2).map { it.toInt(16) }
                    } else emptyList()
                    val sizeMatch = replacementBytes.size == bytes.size

                    if (replacementBytes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Replacement (${replacementBytes.size} bytes)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sizeMatch) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = formatHexDisplay(normalizedReplacement),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (sizeMatch) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )

                        if (!sizeMatch) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Size mismatch: read ${bytes.size} bytes, replacement is ${replacementBytes.size} bytes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = descriptionInput,
            onValueChange = { descriptionInput = it },
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (replacementByteCount > 8) {
                    pendingPatchOffset = offsetInput
                    pendingPatchBytes = patchInput
                    pendingPatchDesc = descriptionInput
                    showLargePatchWarning = true
                } else {
                    doAddPatch(offsetInput, patchInput, descriptionInput)
                    patchInput = ""
                    descriptionInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = offsetInput.isNotBlank() && patchInput.isNotBlank() && currentReadResult != null
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add to Patch List")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (patches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Patch List (${patches.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { viewModel.clearPatches() }) {
                    Text("Clear All")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(patches, key = { it.id }) { patch ->
                    PatchCard(
                        patch = patch,
                        onToggle = { viewModel.togglePatch(patch.id) },
                        onEdit = {
                            editingPatch = patch
                            showEditDialog = true
                        },
                        onDelete = { viewModel.deletePatch(patch.id) }
                    )
                }
            }
        }
    }

    if (showLargePatchWarning) {
        AlertDialog(
            onDismissRequest = { showLargePatchWarning = false },
            title = { Text("Large Patch Warning") },
            text = {
                Text("This patch modifies ${pendingPatchBytes.replace(" ", "").length / 2} bytes. Patches larger than 8 bytes can affect library runtime behavior and may cause crashes or instability. Are you sure you want to proceed?")
            },
            confirmButton = {
                TextButton(onClick = {
                    doAddPatch(pendingPatchOffset, pendingPatchBytes, pendingPatchDesc)
                    patchInput = ""
                    descriptionInput = ""
                    showLargePatchWarning = false
                }) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLargePatchWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog && editingPatch != null) {
        EditPatchDialog(
            patch = editingPatch!!,
            viewModel = viewModel,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                viewModel.updatePatch(editingPatch!!.id, updated)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun EditPatchDialog(
    patch: PatchEntry,
    viewModel: LibEditorViewModel,
    onDismiss: () -> Unit,
    onSave: (PatchEntry) -> Unit
) {
    var offset by remember { mutableStateOf(patch.offset) }
    var replacement by remember { mutableStateOf(patch.replacementBytes) }
    var description by remember { mutableStateOf(patch.description) }

    val replacementByteCount = remember(replacement) {
        val cleaned = replacement.replace(" ", "").uppercase()
        if (cleaned.isNotEmpty() && cleaned.length % 2 == 0 && cleaned.all { it in '0'..'9' || it in 'A'..'F' }) {
            cleaned.length / 2
        } else 0
    }

    LaunchedEffect(offset, replacementByteCount) {
        if (replacementByteCount > 0) {
            val parsed = viewModel.parseHexOffsetPublic(offset)
            if (parsed != null && parsed >= 0) {
                viewModel.readOffset(offset, replacementByteCount)
            }
        }
    }

    val currentReadResult by viewModel.currentReadResult.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
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

                currentReadResult?.let { result ->
                    val hex = result.first
                    val bytes = result.second
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
                onSave(
                    patch.copy(
                        offset = offset,
                        originalBytes = normalizedOriginal,
                        replacementBytes = replacement,
                        description = description
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PatchCard(
    patch: PatchEntry,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
                    IconButton(onClick = onEdit) {
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
}

fun formatHexDisplay(hex: String): String {
    val cleaned = hex.replace(" ", "").uppercase()
    return cleaned.chunked(2).joinToString(" ")
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
