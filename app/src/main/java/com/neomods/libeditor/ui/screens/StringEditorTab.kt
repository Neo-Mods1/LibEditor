package com.neomods.libeditor.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neomods.libeditor.model.ExtractedString
import com.neomods.libeditor.viewmodel.LibEditorViewModel

@Composable
fun StringEditorTab(viewModel: LibEditorViewModel) {
    val extractedStrings by viewModel.extractedStrings.collectAsState()
    val filteredStrings by viewModel.filteredStrings.collectAsState()
    val selectedString by viewModel.selectedString.collectAsState()
    val searchQuery by viewModel.stringSearchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showReplaceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "String Editor",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.filterStrings(it) },
            label = { Text("Search strings...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.filterStrings("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (extractedStrings.isEmpty()) {
            Button(
                onClick = { viewModel.extractStrings() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extract Strings")
            }
        } else {
            Text(
                text = "Found ${filteredStrings.size} strings",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = filteredStrings,
                    key = { "${it.offset}_${it.value}" }
                ) { string ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = string.value,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "0x${string.offset.toString(16).uppercase()} | ${string.encoding.displayName} | ${string.length}B",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectString(string)
                                showReplaceDialog = true
                            }
                    )
                }
            }
        }
    }

    if (showReplaceDialog && selectedString != null) {
        ReplaceStringDialog(
            string = selectedString!!,
            onDismiss = {
                showReplaceDialog = false
                viewModel.selectString(null)
            },
            onReplace = { replacement ->
                viewModel.addStringEdit(replacement)
                showReplaceDialog = false
            }
        )
    }
}

@Composable
fun ReplaceStringDialog(
    string: ExtractedString,
    onDismiss: () -> Unit,
    onReplace: (String) -> Unit
) {
    var replacement by remember { mutableStateOf("") }
    var hasExceededSize by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Queue String Edit") },
        text = {
            Column {
                Text(
                    text = "Original",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = string.value,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Offset: 0x${string.offset.toString(16).uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Size: ${string.length} bytes",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = replacement,
                    onValueChange = {
                        replacement = it
                        hasExceededSize = it.toByteArray().size > string.length
                    },
                    label = { Text("Replacement") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = hasExceededSize,
                    supportingText = {
                        if (hasExceededSize) {
                            Text(
                                text = "Exceeds original size (${string.length} bytes).",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("${replacement.toByteArray().size}/${string.length} bytes")
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onReplace(replacement) },
                enabled = replacement.isNotBlank() && !hasExceededSize
            ) {
                Text("Queue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
