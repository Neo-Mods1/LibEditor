package com.neomods.libeditor.service

import android.content.Context
import com.neomods.libeditor.model.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class JniBridge(private val context: Context) {

    companion object {
        init {
            System.loadLibrary("NeoLibEditor")
        }

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    private external fun nativeGetLibraryInfo(filePath: String): String
    private external fun nativeReadOffset(filePath: String, offset: Long, length: Int): String
    private external fun nativeApplyPatches(filePath: String, patchesJson: String, outputPath: String): String
    private external fun nativeExtractStrings(filePath: String): String
    private external fun nativeReplaceString(
        filePath: String,
        offset: Long,
        originalLength: Int,
        replacement: String,
        outputPath: String
    ): String

    fun getLibraryInfo(filePath: String): Result<LibraryInfo> = try {
        val response = nativeGetLibraryInfo(filePath)
        val native = json.decodeFromString<NativeLibraryInfo>(response)
        Result.success(
            LibraryInfo(
                name = native.name,
                architecture = Architecture.fromString(native.architecture),
                fileSize = native.fileSize,
                sectionCount = native.sectionCount,
                stringCount = native.stringCount,
                entryPoint = native.entryPoint,
                isOpen = true
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun readOffset(filePath: String, offset: Long, length: Int): Result<Pair<String, List<Int>>> = try {
        val response = nativeReadOffset(filePath, offset, length)
        val native = json.decodeFromString<NativeReadResult>(response)
        if (native.success) {
            Result.success(Pair(native.hex, native.bytes))
        } else {
            Result.failure(Exception(native.error))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun applyPatches(
        filePath: String,
        patches: List<PatchEntry>,
        outputPath: String
    ): Result<String> = try {
        val enabledPatches = patches.filter { it.enabled }
        val patchesJson = json.encodeToString(ListSerializer(PatchEntry.serializer()), enabledPatches)
        val response = nativeApplyPatches(filePath, patchesJson, outputPath)
        val native = json.decodeFromString<NativePatchResult>(response)
        if (native.success) {
            Result.success(native.outputPath)
        } else {
            Result.failure(Exception(native.error))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun extractStrings(filePath: String): Result<List<ExtractedString>> = try {
        val response = nativeExtractStrings(filePath)
        val nativeList = json.decodeFromString<List<NativeExtractedString>>(response)
        Result.success(
            nativeList.map {
                ExtractedString(
                    offset = it.offset,
                    value = it.value,
                    encoding = StringEncoding.entries.find { e -> e.name.equals(it.encoding, ignoreCase = true) }
                        ?: StringEncoding.UNKNOWN,
                    length = it.length
                )
            }
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun replaceString(
        filePath: String,
        offset: Long,
        originalLength: Int,
        replacement: String,
        outputPath: String
    ): Result<String> = try {
        val response = nativeReplaceString(filePath, offset, originalLength, replacement, outputPath)
        val native = json.decodeFromString<NativeStringResult>(response)
        if (native.success) {
            Result.success(native.outputPath)
        } else {
            Result.failure(Exception(native.error))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun generateOutputPath(originalPath: String, suffix: String = "patched"): String {
        val file = File(originalPath)
        val name = file.nameWithoutExtension
        val ext = file.extension
        val parent = file.parent ?: ""
        return File(parent, "${name}_${suffix}.$ext").absolutePath
    }
}
