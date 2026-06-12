package com.neomods.libeditor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neomods.libeditor.model.*
import com.neomods.libeditor.repository.LibEditorRepository
import com.neomods.libeditor.service.JniBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LibEditorRepository
    private val context = application

    private val _libraryInfo = MutableStateFlow(LibraryInfo())
    val libraryInfo: StateFlow<LibraryInfo> = _libraryInfo.asStateFlow()

    private val _patches = MutableStateFlow<List<PatchEntry>>(emptyList())
    val patches: StateFlow<List<PatchEntry>> = _patches.asStateFlow()

    private val _extractedStrings = MutableStateFlow<List<ExtractedString>>(emptyList())
    val extractedStrings: StateFlow<List<ExtractedString>> = _extractedStrings.asStateFlow()

    private val _filteredStrings = MutableStateFlow<List<ExtractedString>>(emptyList())
    val filteredStrings: StateFlow<List<ExtractedString>> = _filteredStrings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _currentReadResult = MutableStateFlow<Pair<String, List<Int>>?>(null)
    val currentReadResult: StateFlow<Pair<String, List<Int>>?> = _currentReadResult.asStateFlow()

    private val _selectedString = MutableStateFlow<ExtractedString?>(null)
    val selectedString: StateFlow<ExtractedString?> = _selectedString.asStateFlow()

    private val _stringSearchQuery = MutableStateFlow("")
    val stringSearchQuery: StateFlow<String> = _stringSearchQuery.asStateFlow()

    init {
        val jniBridge = JniBridge(application)
        repository = LibEditorRepository(jniBridge, application)
    }

    fun loadLibrary(filePath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                repository.getLibraryInfo(filePath)
            }
            result.onSuccess { info ->
                _libraryInfo.value = info
                _patches.value = emptyList()
                _extractedStrings.value = emptyList()
                _filteredStrings.value = emptyList()
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to load library"
            }
            _isLoading.value = false
        }
    }

    fun loadLibraryFromUri(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val path = withContext(Dispatchers.IO) {
                    copyUriToEditor(uri)
                }
                if (path != null) {
                    loadLibrary(path)
                } else {
                    _errorMessage.value = "Could not read file"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error reading file: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private fun copyUriToEditor(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileName(uri) ?: "temp_library.so"
            val outputFile = File(repository.getEditorDir(), fileName)

            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name = "temp_library.so"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    name = it.getString(idx) ?: name
                }
            }
        }
        return name
    }

    fun readOffset(offsetHex: String, length: Int = 4) {
        val offset = parseHexOffset(offsetHex) ?: run {
            _errorMessage.value = "Invalid hex offset"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                repository.readOffset(offset, length)
            }
            result.onSuccess { data ->
                _currentReadResult.value = data
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to read offset"
            }
            _isLoading.value = false
        }
    }

    fun addPatch(
        offset: String,
        replacementBytes: String,
        description: String = ""
    ) {
        val current = _currentReadResult.value ?: run {
            _errorMessage.value = "Read the offset first"
            return
        }

        val normalizedOffset = normalizeHex(offset)
        val normalizedReplacement = normalizeHex(replacementBytes)
        val originalBytes = normalizeHex(current.first)

        if (normalizedReplacement.length != originalBytes.length) {
            _errorMessage.value = "Replacement must be ${originalBytes.length / 2} bytes"
            return
        }

        val patch = PatchEntry(
            id = System.currentTimeMillis().toString(),
            offset = normalizedOffset,
            originalBytes = originalBytes,
            replacementBytes = normalizedReplacement,
            enabled = true,
            description = description
        )

        _patches.value = _patches.value + patch
        _successMessage.value = "Patch added"
        _currentReadResult.value = null
    }

    fun updatePatch(id: String, patch: PatchEntry) {
        _patches.value = _patches.value.map { if (it.id == id) patch else it }
    }

    fun togglePatch(id: String) {
        _patches.value = _patches.value.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
    }

    fun deletePatch(id: String) {
        _patches.value = _patches.value.filter { it.id != id }
    }

    fun clearPatches() {
        _patches.value = emptyList()
    }

    fun applySinglePatch(patchId: String) {
        val patch = _patches.value.find { it.id == patchId } ?: run {
            _errorMessage.value = "Patch not found"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                repository.applySinglePatch(patch)
            }
            result.onSuccess { path ->
                _successMessage.value = "Patch applied to: $path"
                reloadFromEditor()
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to apply patch"
            }
            _isLoading.value = false
        }
    }

    fun applyAllPatches() {
        val enabledPatches = _patches.value.filter { it.enabled }
        if (enabledPatches.isEmpty()) {
            _errorMessage.value = "No enabled patches"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                repository.applyPatches(enabledPatches)
            }
            result.onSuccess { path ->
                _successMessage.value = "Patches saved to: $path"
                reloadFromEditor()
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to apply patches"
            }
            _isLoading.value = false
        }
    }

    private fun reloadFromEditor() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.reloadFromEditor()
            }
            result.onSuccess { path ->
                val infoResult = withContext(Dispatchers.IO) {
                    repository.getLibraryInfo(path)
                }
                infoResult.onSuccess { info ->
                    _libraryInfo.value = info
                }
            }
        }
    }

    fun extractStrings() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                repository.extractStrings()
            }
            result.onSuccess { strings ->
                _extractedStrings.value = strings
                _filteredStrings.value = strings
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to extract strings"
            }
            _isLoading.value = false
        }
    }

    fun filterStrings(query: String) {
        _stringSearchQuery.value = query
        if (query.isBlank()) {
            _filteredStrings.value = _extractedStrings.value
        } else {
            _filteredStrings.value = _extractedStrings.value.filter {
                it.value.contains(query, ignoreCase = true)
            }
        }
    }

    fun selectString(string: ExtractedString?) {
        _selectedString.value = string
    }

    fun replaceSelectedString(replacement: String) {
        val selected = _selectedString.value ?: run {
            _errorMessage.value = "No string selected"
            return
        }

        if (replacement.toByteArray().size > selected.length) {
            _errorMessage.value = "Replacement exceeds original size (${selected.length} bytes). Use strict mode."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                repository.replaceString(
                    offset = selected.offset,
                    originalLength = selected.length,
                    replacement = replacement
                )
            }
            result.onSuccess { path ->
                _successMessage.value = "String replaced, saved to: $path"
                _selectedString.value = null
                reloadFromEditor()
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to replace string"
            }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccess() {
        _successMessage.value = null
    }

    private fun parseHexOffset(hex: String): Long? {
        return try {
            val cleaned = hex.trim().removePrefix("0x").removePrefix("0X").replace(" ", "")
            cleaned.toLong(16)
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeHex(hex: String): String {
        return hex.trim()
            .replace("0x", "", ignoreCase = true)
            .replace("0X", "", ignoreCase = true)
            .replace(" ", "")
            .uppercase()
    }
}
