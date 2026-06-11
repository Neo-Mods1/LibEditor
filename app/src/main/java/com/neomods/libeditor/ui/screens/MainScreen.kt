package com.neomods.libeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neomods.libeditor.model.Architecture
import com.neomods.libeditor.ui.components.*
import com.neomods.libeditor.viewmodel.LibEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: LibEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val libraryInfo by viewModel.libraryInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadLibraryFromUri(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("LibEditor", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open .so file")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SnackbarHost(
                hostState = remember { SnackbarHostState() },
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )

            LibraryInfoCard(libraryInfo)

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (libraryInfo.isOpen) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Address Patching") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("String Editor") }
                    )
                }

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn() + slideInHorizontally { it } togetherWith
                                fadeOut() + slideOutHorizontally { -it }
                    },
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        0 -> AddressPatchingTab(viewModel)
                        1 -> StringEditorTab(viewModel)
                    }
                }
            } else {
                EmptyStateCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            viewModel.clearError()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            viewModel.clearSuccess()
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    errorMessage?.let { msg ->
        ErrorSnackbar(message = msg, onDismiss = { viewModel.clearError() })
    }

    successMessage?.let { msg ->
        SuccessSnackbar(message = msg, onDismiss = { viewModel.clearSuccess() })
    }
}

@Composable
fun LibraryInfoCard(info: com.neomods.libeditor.model.LibraryInfo) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (info.isOpen) info.name else "No Library Loaded",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (info.isOpen) {
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("Architecture", info.architecture.displayName)
                InfoRow("File Size", formatFileSize(info.fileSize))
                InfoRow("Sections", info.sectionCount.toString())
                InfoRow("Strings", info.stringCount.toString())
                InfoRow("Entry Point", "0x${info.entryPoint.toString(16).uppercase()}")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
