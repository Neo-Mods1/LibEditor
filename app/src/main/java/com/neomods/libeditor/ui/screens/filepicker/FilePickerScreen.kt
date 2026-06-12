package com.neomods.libeditor.ui.screens.filepicker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neomods.libeditor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    onFileSelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    copyToInternal(context, it)
                }
                if (result != null) {
                    onFileSelected(result)
                } else {
                    errorMessage = "Failed to copy file"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        val libDir = File(context.filesDir, "libraries")
        libDir.mkdirs()
        currentDir = libDir
        files = libDir.listFiles()
            ?.filter { it.isFile && it.extension == "so" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Select .so File",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Browse")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pick .so File",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select an ELF shared library to edit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open File Picker")
                    }
                }
            }

            if (files.isNotEmpty()) {
                Text(
                    text = "Recent Libraries",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(files) { file ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = file.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = formatSize(file.length()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            copyInternalToLibrary(context, file)
                                        }
                                        if (result != null) {
                                            onFileSelected(result)
                                        } else {
                                            errorMessage = "Failed to load file"
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No libraries yet.\nPick a .so file to get started.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    errorMessage?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        ) {
            Text(msg)
        }
    }
}

private fun copyToInternal(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = getFileName(context, uri) ?: "unknown.so"

        val libDir = File(context.filesDir, "libraries")
        libDir.mkdirs()
        val outputFile = File(libDir, fileName)

        inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }

        if (outputFile.length() == 0L) {
            outputFile.delete()
            return null
        }

        outputFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun copyInternalToLibrary(context: android.content.Context, source: File): String? {
    return try {
        val libDir = File(context.filesDir, "libraries")
        libDir.mkdirs()
        val dest = File(libDir, source.name)

        if (source.absolutePath != dest.absolutePath) {
            source.copyTo(dest, overwrite = true)
        }

        dest.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                name = it.getString(idx)
            }
        }
    }
    return name
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
