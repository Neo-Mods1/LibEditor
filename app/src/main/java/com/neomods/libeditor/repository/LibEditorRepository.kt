package com.neomods.libeditor.repository

import com.neomods.libeditor.model.*
import com.neomods.libeditor.service.JniBridge

class LibEditorRepository(private val jniBridge: JniBridge) {

    private var currentFilePath: String? = null

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

    fun applyPatches(patches: List<PatchEntry>, outputPath: String? = null): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val out = outputPath ?: jniBridge.generateOutputPath(path)
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
        val out = outputPath ?: jniBridge.generateOutputPath(path, "string_patched")
        return jniBridge.replaceString(path, offset, originalLength, replacement, out)
    }

    fun generateOutputPath(suffix: String = "patched"): String {
        val path = currentFilePath ?: return "output.so"
        return jniBridge.generateOutputPath(path, suffix)
    }
}
