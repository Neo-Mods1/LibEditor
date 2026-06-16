package com.neomods.libeditor.ui.screens.editor

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neomods.libeditor.R
import com.neomods.libeditor.ui.components.ErrorDialog
import com.neomods.libeditor.ui.screens.*
import com.neomods.libeditor.viewmodel.LibEditorViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    libPath: String,
    onNavigateBack: () -> Unit
) {
    val viewModel: LibEditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val libraryInfo by viewModel.libraryInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val patches by viewModel.patches.collectAsState()
    val stringEdits by viewModel.stringEdits.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var infoSheetOffset by remember { mutableFloatStateOf(0f) }
    var showInfoSheet by remember { mutableStateOf(false) }

    val totalModifications = patches.size + stringEdits.size

    LaunchedEffect(libPath) {
        viewModel.loadLibrary(libPath)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = libraryInfo.name.ifEmpty { "Editor" },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (libraryInfo.isOpen) {
                        if (totalModifications > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text("$totalModifications")
                                    }
                                }
                            ) {
                                IconButton(onClick = { viewModel.saveAllModifications() }) {
                                    Icon(Icons.Default.Save, contentDescription = "Save All")
                                }
                            }
                        } else {
                            IconButton(onClick = { viewModel.saveAllModifications() }) {
                                Icon(Icons.Default.Save, contentDescription = "Save All")
                            }
                        }
                        IconButton(onClick = { viewModel.revertToOriginal() }) {
                            Icon(Icons.Default.Restore, contentDescription = "Revert to Original")
                        }
                        IconButton(onClick = { showInfoSheet = !showInfoSheet }) {
                            Icon(Icons.Default.Info, contentDescription = "Library Info")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (libraryInfo.isOpen) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.tab_patches)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.tab_strings)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Mods")
                                    if (totalModifications > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Badge {
                                            Text("$totalModifications")
                                        }
                                    }
                                }
                            }
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
                            2 -> ModificationsTab(viewModel)
                        }
                    }
                } else {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            if (showInfoSheet && libraryInfo.isOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (infoSheetOffset > 100) {
                                        showInfoSheet = false
                                    }
                                    infoSheetOffset = 0f
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    infoSheetOffset += dragAmount
                                }
                            )
                        }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .offset { IntOffset(0, infoSheetOffset.roundToInt()) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    modifier = Modifier.width(40.dp).height(4.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                ) {}
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = libraryInfo.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow(stringResource(R.string.architecture), libraryInfo.architecture.displayName)
                            InfoRow(stringResource(R.string.file_size), formatFileSize(libraryInfo.fileSize))
                            InfoRow(stringResource(R.string.sections), libraryInfo.sectionCount.toString())
                            InfoRow(stringResource(R.string.strings), libraryInfo.stringCount.toString())
                            InfoRow(stringResource(R.string.entry_point), "0x${libraryInfo.entryPoint.toString(16).uppercase()}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.drag_to_close),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
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
        ErrorDialog(message = msg, onDismiss = { viewModel.clearError() })
    }

    successMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSuccess() },
            title = { Text("Saved") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSuccess() }) {
                    Text("OK")
                }
            }
        )
    }
}
