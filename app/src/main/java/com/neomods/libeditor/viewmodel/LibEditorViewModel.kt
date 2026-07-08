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

data class StringEditEntry(
    val id: String = System.currentTimeMillis().toString(),
    val offset: Long,
    val originalLength: Int,
    val replacement: String,
    val originalValue: String,
    val enabled: Boolean = true
)

class LibEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LibEditorRepository
    private val context = application

    private val _libraryInfo = MutableStateFlow(LibraryInfo())
    val libraryInfo: StateFlow<LibraryInfo> = _libraryInfo.asStateFlow()

    private val _patches = MutableStateFlow<List<PatchEntry>>(emptyList())
    val patches: StateFlow<List<PatchEntry>> = _patches.asStateFlow()

    private val _stringEdits = MutableStateFlow<List<StringEditEntry>>(emptyList())
    val stringEdits: StateFlow<List<StringEditEntry>> = _stringEdits.asStateFlow()

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
                _stringEdits.value = emptyList()
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

            val libDir = File(context.filesDir, "libraries")
            libDir.mkdirs()
            val outputFile = File(libDir, fileName)

            inputStream.use { input ->
                outputFile.outputStream().use { output ->
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
            _errorMessage.value = "Read the offset first before adding a patch"
            return
        }

        val normalizedOffset = normalizeHex(offset) ?: run {
            _errorMessage.value = "Invalid hex in offset"
            return
        }
        val normalizedReplacement = normalizeHex(replacementBytes) ?: run {
            _errorMessage.value = "Invalid hex in replacement bytes"
            return
        }
        val originalBytes = normalizeHex(current.first) ?: run {
            _errorMessage.value = "Invalid hex in read result"
            return
        }

        if (normalizedReplacement.length != originalBytes.length) {
            _errorMessage.value = "Replacement must be ${originalBytes.length / 2} bytes (got ${normalizedReplacement.length / 2})"
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

    fun addStringEdit(replacement: String) {
        val selected = _selectedString.value ?: run {
            _errorMessage.value = "No string selected"
            return
        }

        val entry = StringEditEntry(
            offset = selected.offset,
            originalLength = selected.length,
            replacement = replacement,
            originalValue = selected.value
        )

        _stringEdits.value = _stringEdits.value + entry
        _selectedString.value = null
    }

    fun toggleStringEdit(id: String) {
        _stringEdits.value = _stringEdits.value.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
    }

    fun deleteStringEdit(id: String) {
        _stringEdits.value = _stringEdits.value.filter { it.id != id }
    }

    fun clearStringEdits() {
        _stringEdits.value = emptyList()
    }

    fun saveAllModifications() {
        val enabledPatches = _patches.value.filter { it.enabled }
        val enabledEdits = _stringEdits.value.filter { it.enabled }

        if (enabledPatches.isEmpty() && enabledEdits.isEmpty()) {
            _errorMessage.value = "No modifications to save"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val backupResult = withContext(Dispatchers.IO) {
                repository.backupOriginal()
            }
            backupResult.onFailure { e ->
                _errorMessage.value = "Backup failed: ${e.message}"
                _isLoading.value = false
                return@launch
            }

            var outputPath: String? = null

            if (enabledPatches.isNotEmpty()) {
                val patchResult = withContext(Dispatchers.IO) {
                    repository.applyPatches(enabledPatches)
                }
                patchResult.onSuccess { path -> outputPath = path }
                patchResult.onFailure { e ->
                    _errorMessage.value = "Patch error: ${e.message}"
                    _isLoading.value = false
                    return@launch
                }
            }

            if (enabledEdits.isNotEmpty()) {
                val patchedFilePath = outputPath ?: withContext(Dispatchers.IO) {
                    repository.getCurrentFilePath()
                }
                if (patchedFilePath == null) {
                    _errorMessage.value = "No file to apply string edits to"
                    _isLoading.value = false
                    return@launch
                }

                var currentFile = patchedFilePath ?: return@launch
                var redirectCount = 0
                for (edit in enabledEdits) {
                    val editResult = withContext(Dispatchers.IO) {
                        repository.replaceString(currentFile, edit.offset, edit.originalLength, edit.replacement)
                    }
                    editResult.onSuccess { result ->
                        outputPath = result.outputPath
                        currentFile = result.outputPath
                        if (result.redirected) {
                            redirectCount++
                        }
                    }
                    editResult.onFailure { e ->
                        _errorMessage.value = "String edit error: ${e.message}"
                        _isLoading.value = false
                        return@launch
                    }
                }
                if (redirectCount > 0) {
                    _successMessage.value = "Redirected $redirectCount string(s) to new memory locations"
                }
            }

            val savedPath = outputPath
            if (savedPath != null) {
                _successMessage.value = "Saved to: $savedPath"
                reloadAfterPatch()
            } else {
                _errorMessage.value = "Failed to save modifications"
            }
            _isLoading.value = false
        }
    }

    fun revertToOriginal() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                repository.restoreFromBackup()
            }
            result.onSuccess { path ->
                _patches.value = emptyList()
                _stringEdits.value = emptyList()
                _successMessage.value = "Reverted to original"
                loadLibrary(path)
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to revert"
            }
            _isLoading.value = false
        }
    }

    private fun reloadAfterPatch() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.reloadFromOutput()
            }
            result.onSuccess { path ->
                val infoResult = withContext(Dispatchers.IO) {
                    repository.getLibraryInfo(path)
                }
                infoResult.onSuccess { info ->
                    _libraryInfo.value = info
                    _patches.value = emptyList()
                    _stringEdits.value = emptyList()
                }

                val stringsResult = withContext(Dispatchers.IO) {
                    repository.extractStrings()
                }
                stringsResult.onSuccess { strings ->
                    _extractedStrings.value = strings
                    _filteredStrings.value = strings
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

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccess() {
        _successMessage.value = null
    }

    fun exportPatches(): String? {
        val enabledPatches = _patches.value.filter { it.enabled }
        if (enabledPatches.isEmpty()) {
            _errorMessage.value = "No patches to export"
            return null
        }
        return try {
            val json = kotlinx.serialization.json.Json { prettyPrint = true }
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(PatchEntry.serializer()), enabledPatches)
        } catch (e: Exception) {
            _errorMessage.value = "Export failed: ${e.message}"
            null
        }
    }

    fun importPatches(jsonString: String): Boolean {
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            val imported = json.decodeFromString<List<PatchEntry>>(jsonString)
            if (imported.isEmpty()) {
                _errorMessage.value = "No patches found in file"
                return false
            }
            _patches.value = _patches.value + imported.map { it.copy(id = System.currentTimeMillis().toString() + Math.random().toString()) }
            _successMessage.value = "Imported ${imported.size} patch(es)"
            true
        } catch (e: Exception) {
            _errorMessage.value = "Import failed: ${e.message}"
            false
        }
    }

    fun exportStringEdits(): String? {
        val enabledEdits = _stringEdits.value.filter { it.enabled }
        if (enabledEdits.isEmpty()) {
            _errorMessage.value = "No string edits to export"
            return null
        }
        return try {
            val jsonArray = org.json.JSONArray()
            for (edit in enabledEdits) {
                val obj = org.json.JSONObject()
                obj.put("offset", edit.offset)
                obj.put("originalLength", edit.originalLength)
                obj.put("replacement", edit.replacement)
                obj.put("originalValue", edit.originalValue)
                obj.put("enabled", edit.enabled)
                jsonArray.put(obj)
            }
            jsonArray.toString(2)
        } catch (e: Exception) {
            _errorMessage.value = "Export failed: ${e.message}"
            null
        }
    }

    private fun parseHexOffset(hex: String): Long? {
        return try {
            val cleaned = hex.trim().removePrefix("0x").removePrefix("0X").replace(" ", "")
            if (cleaned.isEmpty()) return null
            if (cleaned.length > 16) return null
            java.lang.Long.parseUnsignedLong(cleaned, 16)
        } catch (e: Exception) {
            null
        }
    }

    fun parseHexOffsetPublic(hex: String): Long? = parseHexOffset(hex)

    private fun normalizeHex(hex: String): String? {
        val cleaned = hex.trim()
            .replace("0x", "", ignoreCase = true)
            .replace(" ", "")
            .uppercase()

        if (cleaned.isEmpty()) return null
        if (!cleaned.all { it in '0'..'9' || it in 'A'..'F' }) return null

        val padded = if (cleaned.length % 2 != 0) "0$cleaned" else cleaned
        return padded
    }
}
