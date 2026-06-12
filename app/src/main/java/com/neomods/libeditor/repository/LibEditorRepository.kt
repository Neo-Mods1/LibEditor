package com.neomods.libeditor.repository

import android.content.Context
import com.neomods.libeditor.model.*
import com.neomods.libeditor.service.JniBridge
import com.neomods.libeditor.storage.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

class LibEditorRepository(private val jniBridge: JniBridge, private val context: Context) {

    private var currentFilePath: String? = null
    private val settingsManager = SettingsManager(context)

    fun getEditorDir(): File {
        val dir = File(context.filesDir, "libraries")
        dir.mkdirs()
        return dir
    }

    fun getCurrentFilePath(): String? = currentFilePath

    fun setCurrentFilePath(path: String) {
        currentFilePath = path
    }

    fun getLibraryInfo(filePath: String): Result<LibraryInfo> {
        currentFilePath = filePath
        return jniBridge.getLibraryInfo(filePath)
    }

    fun readOffset(offset: Long, length: Int = 4): Result<Pair<String, List<Int>>> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        return jniBridge.readOffset(path, offset, length)
    }

    fun applySinglePatch(patch: PatchEntry, outputPath: String? = null): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val out = outputPath ?: getEditorOutputPath(path)
        return jniBridge.applyPatches(path, listOf(patch), out)
    }

    fun applyPatches(patches: List<PatchEntry>, outputPath: String? = null): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val out = outputPath ?: getEditorOutputPath(path)
        return jniBridge.applyPatches(path, patches, out)
    }

    fun extractStrings(): Result<List<ExtractedString>> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        return jniBridge.extractStrings(path)
    }

    fun replaceString(
        offset: Long,
        originalLength: Int,
        replacement: String,
        outputPath: String? = null
    ): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val out = outputPath ?: getEditorOutputPath(path)
        return jniBridge.replaceString(path, offset, originalLength, replacement, out)
    }

    fun getEditorOutputPath(originalPath: String): String {
        val file = File(originalPath)
        return File(getEditorDir(), file.name).absolutePath
    }

    fun reloadFromEditor(): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val file = File(path)
        val editorPath = File(getEditorDir(), file.name).absolutePath
        if (File(editorPath).exists()) {
            currentFilePath = editorPath
            return Result.success(editorPath)
        }
        return Result.failure(IllegalStateException("File not found in Editor folder"))
    }
}
