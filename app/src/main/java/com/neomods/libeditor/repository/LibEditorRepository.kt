package com.neomods.libeditor.repository

import android.content.Context
import android.os.Environment
import com.neomods.libeditor.model.*
import com.neomods.libeditor.service.JniBridge
import com.neomods.libeditor.storage.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

class LibEditorRepository(private val jniBridge: JniBridge, private val context: Context) {

    private var currentFilePath: String? = null
    private val settingsManager = SettingsManager(context)

    fun getInternalLibDir(): File {
        val dir = File(context.filesDir, "libraries")
        dir.mkdirs()
        return dir
    }

    fun getBackupDir(): File {
        val dir = File(context.filesDir, "backups")
        dir.mkdirs()
        return dir
    }

    fun getOutputDir(): File {
        val location = runBlocking { settingsManager.editLocation.first() }
        val dir = File(location)
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

    fun readOffset(path: String, offset: Long, length: Int): Result<Pair<String, List<Int>>> {
        return jniBridge.readOffset(path, offset, length)
    }

    fun applyPatches(patches: List<PatchEntry>): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val fileName = File(path).name
        val outputPath = File(getOutputDir(), fileName).absolutePath
        return jniBridge.applyPatches(path, patches, outputPath)
    }

    fun replaceString(
        filePath: String,
        offset: Long,
        originalLength: Int,
        replacement: String
    ): Result<NativeStringResult> {
        val fileName = File(filePath).name
        val outputPath = File(getOutputDir(), fileName).absolutePath
        return jniBridge.replaceString(filePath, offset, originalLength, replacement, outputPath)
    }

    fun extractStrings(): Result<List<ExtractedString>> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        return jniBridge.extractStrings(path)
    }

    fun backupOriginal(): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val source = File(path)
        val backup = File(getBackupDir(), source.name)
        source.copyTo(backup, overwrite = true)
        return Result.success(backup.absolutePath)
    }

    fun restoreFromBackup(): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val source = File(path)
        val backup = File(getBackupDir(), source.name)
        if (!backup.exists()) {
            return Result.failure(IllegalStateException("No backup found"))
        }
        backup.copyTo(source, overwrite = true)
        return Result.success(source.absolutePath)
    }

    fun reloadFromOutput(): Result<String> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        val fileName = File(path).name
        val outputPath = File(getOutputDir(), fileName).absolutePath

        if (!File(outputPath).exists()) {
            return Result.failure(IllegalStateException("Patched file not found in output"))
        }

        val internalPath = File(getInternalLibDir(), fileName).absolutePath
        File(outputPath).copyTo(File(internalPath), overwrite = true)

        currentFilePath = internalPath
        return Result.success(internalPath)
    }

    fun searchBytes(hexPattern: String): Result<List<Pair<Long, ByteArray>>> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        return jniBridge.searchBytes(path, hexPattern)
    }

    fun loadSections(): Result<List<ElfSection>> {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file loaded"))
        return jniBridge.loadSections(path)
    }
}
