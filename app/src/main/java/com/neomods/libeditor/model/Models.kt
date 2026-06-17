package com.neomods.libeditor.model

import kotlinx.serialization.Serializable

enum class Architecture(val displayName: String) {
    ARM64("ARM64"),
    ARMV7("ARMv7"),
    X86("x86"),
    X86_64("x86_64"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String): Architecture = when (value.lowercase()) {
            "arm64", "aarch64" -> ARM64
            "armv7", "arm", "armeabi-v7a" -> ARMV7
            "x86", "i386", "i686" -> X86
            "x86_64", "x86-64", "amd64" -> X86_64
            else -> UNKNOWN
        }
    }
}

enum class StringEncoding(val displayName: String) {
    ASCII("ASCII"),
    UTF8("UTF-8"),
    UTF16LE("UTF-16 LE"),
    UTF16BE("UTF-16 BE"),
    UNKNOWN("Unknown");
}

@Serializable
data class LibraryInfo(
    val name: String = "",
    val architecture: Architecture = Architecture.UNKNOWN,
    val fileSize: Long = 0L,
    val sectionCount: Int = 0,
    val stringCount: Int = 0,
    val entryPoint: Long = 0L,
    val isOpen: Boolean = false
)

@Serializable
data class PatchEntry(
    val id: String = "",
    val offset: String = "",
    val originalBytes: String = "",
    val replacementBytes: String = "",
    val enabled: Boolean = true,
    val description: String = ""
)

@Serializable
data class ExtractedString(
    val offset: Long = 0L,
    val value: String = "",
    val encoding: StringEncoding = StringEncoding.UNKNOWN,
    val length: Int = 0
)

@Serializable
data class NativeLibraryInfo(
    val name: String = "",
    val architecture: String = "",
    val fileSize: Long = 0L,
    val sectionCount: Int = 0,
    val stringCount: Int = 0,
    val entryPoint: Long = 0L
)

@Serializable
data class NativeExtractedString(
    val offset: Long = 0L,
    val value: String = "",
    val encoding: String = "",
    val length: Int = 0
)

@Serializable
data class NativeReadResult(
    val success: Boolean = false,
    val hex: String = "",
    val bytes: List<Int> = emptyList(),
    val error: String = ""
)

@Serializable
data class NativePatchResult(
    val success: Boolean = false,
    val outputPath: String = "",
    val error: String = ""
)

@Serializable
data class NativeStringResult(
    val success: Boolean = false,
    val outputPath: String = "",
    val error: String = "",
    val redirected: Boolean = false,
    val originalOffset: Long = 0L,
    val newStringOffset: Long = 0L,
    val pointersUpdated: Int = 0
)
