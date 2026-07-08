package com.neomods.libeditor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomods.libeditor.R
import com.neomods.libeditor.viewmodel.LibEditorViewModel

@Composable
fun HexViewerTab(viewModel: LibEditorViewModel) {
    val libraryInfo by viewModel.libraryInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var startOffset by remember { mutableStateOf("0") }
    var byteCount by remember { mutableStateOf("256") }
    var hexData by remember { mutableStateOf<Pair<String, List<Int>>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadBytes() {
        val offset = try {
            val cleaned = startOffset.trim().removePrefix("0x").removePrefix("0X")
            if (cleaned.isEmpty()) 0L else java.lang.Long.parseUnsignedLong(cleaned, 16)
        } catch (e: Exception) {
            errorMessage = "Invalid offset"
            return
        }

        val count = try {
            byteCount.trim().toInt().coerceIn(1, 4096)
        } catch (e: Exception) {
            errorMessage = "Invalid byte count"
            return
        }

        viewModel.readOffset(startOffset, count)
    }

    val currentReadResult by viewModel.currentReadResult.collectAsState()

    LaunchedEffect(currentReadResult) {
        currentReadResult?.let {
            hexData = it
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.hex_viewer),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startOffset,
                        onValueChange = { startOffset = it },
                        label = { Text(stringResource(R.string.offset)) },
                        placeholder = { Text("0x0") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = byteCount,
                        onValueChange = { byteCount = it },
                        label = { Text(stringResource(R.string.bytes_label)) },
                        placeholder = { Text("256") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Button(
                    onClick = { loadBytes() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && startOffset.isNotBlank(),
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
                    Text(stringResource(R.string.load_bytes))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        hexData?.let { (hex, bytes) ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hex_data),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
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

                    Spacer(modifier = Modifier.height(8.dp))

                    val hexLines = hex.split(" ").chunked(16)
                    val offsetValue = try {
                        val cleaned = startOffset.trim().removePrefix("0x").removePrefix("0X")
                        if (cleaned.isEmpty()) 0L else java.lang.Long.parseUnsignedLong(cleaned, 16)
                    } catch (e: Exception) { 0L }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(hexLines) { index, line ->
                            val lineOffset = offsetValue + (index * 16)
                            val offsetStr = "0x${lineOffset.toString(16).uppercase().padStart(8, '0')}"
                            val hexStr = line.joinToString(" ") { it.padStart(2, '0') }
                            val asciiStr = bytes.drop(index * 16).take(16).joinToString("") { b ->
                                if (b in 0x20..0x7e) b.toChar().toString() else "."
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = offsetStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(80.dp)
                                )
                                Text(
                                    text = hexStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = asciiStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } ?: run {
            if (!isLoading) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Enter an offset and load bytes to view",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        errorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Error") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
