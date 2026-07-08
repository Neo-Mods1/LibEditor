package com.neomods.libeditor.service

import android.content.Context
import com.neomods.libeditor.model.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private fun isErrorJson(response: String): Boolean {
        return try {
            val obj = json.decodeFromString<JsonObject>(response)
            obj.containsKey("error") && obj["error"] != null && obj["error"]!!.jsonPrimitive.content.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun getErrorMessage(response: String): String {
        return try {
            val obj = json.decodeFromString<JsonObject>(response)
            obj["error"]?.jsonPrimitive?.content ?: "Unknown error"
        } catch (_: Exception) {
            "Unknown error"
        }
    }

    fun getLibraryInfo(filePath: String): Result<LibraryInfo> = try {
        val response = nativeGetLibraryInfo(filePath)
        if (isErrorJson(response)) {
            Result.failure(Exception(getErrorMessage(response)))
        } else {
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
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun readOffset(filePath: String, offset: Long, length: Int): Result<Pair<String, List<Int>>> = try {
        val response = nativeReadOffset(filePath, offset, length)
        if (isErrorJson(response)) {
            Result.failure(Exception(getErrorMessage(response)))
        } else {
            val native = json.decodeFromString<NativeReadResult>(response)
            if (native.success) {
                Result.success(Pair(native.hex, native.bytes))
            } else {
                Result.failure(Exception(native.error))
            }
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
        if (isErrorJson(response)) {
            Result.failure(Exception(getErrorMessage(response)))
        } else {
            val native = json.decodeFromString<NativePatchResult>(response)
            if (native.success) {
                Result.success(native.outputPath)
            } else {
                Result.failure(Exception(native.error))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun extractStrings(filePath: String): Result<List<ExtractedString>> = try {
        val response = nativeExtractStrings(filePath)
        if (isErrorJson(response)) {
            Result.failure(Exception(getErrorMessage(response)))
        } else {
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
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun replaceString(
        filePath: String,
        offset: Long,
        originalLength: Int,
        replacement: String,
        outputPath: String
    ): Result<NativeStringResult> = try {
        val response = nativeReplaceString(filePath, offset, originalLength, replacement, outputPath)
        if (isErrorJson(response)) {
            Result.failure(Exception(getErrorMessage(response)))
        } else {
            val native = json.decodeFromString<NativeStringResult>(response)
            if (native.success) {
                Result.success(native)
            } else {
                Result.failure(Exception(native.error))
            }
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

    fun searchBytes(filePath: String, hexPattern: String): Result<List<Pair<Long, ByteArray>>> = try {
        val response = nativeSearchBytes(filePath, hexPattern)
        if (isErrorJson(response)) {
            Result.failure(Exception(getErrorMessage(response)))
        } else {
            val obj = json.decodeFromString<JsonObject>(response)
            val success = obj["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            if (success) {
                val matchesJson = obj["matches"]?.toString() ?: "[]"
                val matches = json.decodeFromString<List<SearchResult>>(matchesJson)
                Result.success(matches.map { Pair(it.offset, it.bytes.map { b -> b.toByte() }.toByteArray()) })
            } else {
                Result.failure(Exception(obj["error"]?.jsonPrimitive?.content ?: "Search failed"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun loadSections(filePath: String): Result<List<ElfSection>> = try {
        val response = nativeLoadSections(filePath)
        if (isErrorJson(response)) {
            Result.failure(Exception(getErrorMessage(response)))
        } else {
            val sections = json.decodeFromString<List<ElfSection>>(response)
            Result.success(sections)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private external fun nativeSearchBytes(filePath: String, hexPattern: String): String
    private external fun nativeLoadSections(filePath: String): String
}
