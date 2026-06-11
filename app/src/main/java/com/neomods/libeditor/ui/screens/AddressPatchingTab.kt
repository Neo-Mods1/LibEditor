package com.neomods.libeditor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

    var showPatchDialog by remember { mutableStateOf(false) }
    var editingPatch by remember { mutableStateOf<PatchEntry?>(null) }

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

        Button(
            onClick = { viewModel.readOffset(offsetInput) },
            modifier = Modifier.fillMaxWidth(),
            enabled = offsetInput.isNotBlank() && !isLoading
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
            Text("Read Current Value")
        }

        currentReadResult?.let { (hex, bytes) ->
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Current Value",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatHexDisplay(hex),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Raw: $hex",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = patchInput,
            onValueChange = { patchInput = it },
            label = { Text("Replacement Bytes (e.g., 00 00 A0 E3)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )

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
                viewModel.addPatch(offsetInput, patchInput, descriptionInput)
                patchInput = ""
                descriptionInput = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = offsetInput.isNotBlank() && patchInput.isNotBlank()
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
                Row {
                    TextButton(onClick = { viewModel.clearPatches() }) {
                        Text("Clear All")
                    }
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
                            showPatchDialog = true
                        },
                        onDelete = { viewModel.deletePatch(patch.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.applyAllPatches() },
                modifier = Modifier.fillMaxWidth(),
                enabled = patches.any { it.enabled }
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Apply All Patches")
            }
        }
    }

    if (showPatchDialog && editingPatch != null) {
        EditPatchDialog(
            patch = editingPatch!!,
            onDismiss = { showPatchDialog = false },
            onSave = { updated ->
                viewModel.updatePatch(updated.id, updated)
                showPatchDialog = false
            }
        )
    }
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
                Row {
                    Switch(
                        checked = patch.enabled,
                        onCheckedChange = { onToggle() }
                    )
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
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
                        text = "Original",
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
                        text = "Replacement",
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

@Composable
fun EditPatchDialog(
    patch: PatchEntry,
    onDismiss: () -> Unit,
    onSave: (PatchEntry) -> Unit
) {
    var offset by remember { mutableStateOf(patch.offset) }
    var replacement by remember { mutableStateOf(patch.replacementBytes) }
    var description by remember { mutableStateOf(patch.description) }

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
                onSave(
                    patch.copy(
                        offset = offset,
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

fun formatHexDisplay(hex: String): String {
    val cleaned = hex.replace(" ", "").uppercase()
    return cleaned.chunked(2).joinToString(" ")
}
