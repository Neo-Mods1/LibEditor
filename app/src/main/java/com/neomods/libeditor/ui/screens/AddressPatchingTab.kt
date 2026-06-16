package com.neomods.libeditor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    val readLength = if (replacementByteCount > 0) replacementByteCount else 8

    fun doReadOffset(offset: String, length: Int) {
        viewModel.readOffset(offset, length)
    }

    fun doAddPatch(offset: String, replacement: String, desc: String) {
        viewModel.addPatch(offset, replacement, desc)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Offset Patching",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = offsetInput,
                        onValueChange = { offsetInput = it },
                        label = { Text("Hex Offset") },
                        placeholder = { Text("0x8EC7B8") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = patchInput,
                        onValueChange = { patchInput = it },
                        label = { Text("Replacement Bytes") },
                        placeholder = { Text("FF 83 00 D1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        trailingIcon = {
                            if (replacementByteCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (replacementByteCount > 8)
                                        MaterialTheme.colorScheme.errorContainer
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "${replacementByteCount}B",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (replacementByteCount > 8)
                                            MaterialTheme.colorScheme.onErrorContainer
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        isError = replacementByteCount > 8
                    )

                    if (replacementByteCount > 8) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Patches over 8 bytes require confirmation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { doReadOffset(offsetInput, readLength) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = offsetInput.isNotBlank() && replacementByteCount > 0 && !isLoading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Read $readLength bytes")
                    }
                }
            }
        }

        currentReadResult?.let { (hex, bytes) ->
            item {
                val normalizedReplacement = patchInput.replace(" ", "").uppercase()
                val replacementHex = if (normalizedReplacement.isNotEmpty() && normalizedReplacement.length % 2 == 0) {
                    normalizedReplacement
                } else ""
                val replacementBytes = if (replacementHex.isNotEmpty()) {
                    replacementHex.chunked(2).map { it.toInt(16) }
                } else emptyList()
                val sizeMatch = replacementBytes.size == bytes.size

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = "${bytes.size} bytes",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = formatHexDisplay(hex),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (replacementBytes.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Replacement",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sizeMatch) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (sizeMatch) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "${replacementBytes.size} bytes",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (sizeMatch) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Text(
                                text = formatHexDisplay(replacementHex),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (sizeMatch) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )

                            if (!sizeMatch) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "Size mismatch — read ${bytes.size}B, replacement ${replacementBytes.size}B",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = descriptionInput,
                onValueChange = { descriptionInput = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        item {
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
                enabled = offsetInput.isNotBlank() && patchInput.isNotBlank() && currentReadResult != null,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Patch")
            }
        }

        if (patches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Queued Patches",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${patches.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

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

            item {
                TextButton(
                    onClick = { viewModel.clearPatches() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All")
                }
            }
        }
    }

    if (showLargePatchWarning) {
        AlertDialog(
            onDismissRequest = { showLargePatchWarning = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Large Patch") },
            text = {
                Text("This patch modifies ${pendingPatchBytes.replace(" ", "").length / 2} bytes. Patches larger than 8 bytes can affect library runtime behavior and may cause crashes. Proceed with caution.")
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = offset,
                    onValueChange = { offset = it },
                    label = { Text("Offset") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                currentReadResult?.let { result ->
                    val hex = result.first
                    val bytes = result.second
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Current (${bytes.size}B)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatHexDisplay(hex),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Replacement Bytes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (patch.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = patch.offset,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (patch.description.isNotBlank()) {
                        Text(
                            text = patch.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = patch.enabled,
                        onCheckedChange = { onToggle() }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FROM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatHexDisplay(patch.originalBytes),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatHexDisplay(patch.replacementBytes),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
