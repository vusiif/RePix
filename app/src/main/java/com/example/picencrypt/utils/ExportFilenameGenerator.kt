package com.example.picencrypt.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for generating customized export filenames based on templates, tokens,
 * sanitized original filenames, and sequential numbering.
 */
object ExportFilenameGenerator {

    const val TOKEN_PREFIX = "{PREFIX}"
    const val TOKEN_ORIGINAL = "{ORIGINAL}"
    const val TOKEN_DATE = "{DATE}"
    const val TOKEN_TIME = "{TIME}"
    const val TOKEN_INDEX = "{INDEX}"
    const val TOKEN_ALGO = "{ALGO}"

    const val PATTERN_TIMESTAMP = "{PREFIX}_{DATE}_{TIME}_{INDEX}"
    const val PATTERN_ORIGINAL_PREFIX = "{PREFIX}_{ORIGINAL}"
    const val PATTERN_ALGORITHM_TAGGED = "{PREFIX}_{ALGO}_{ORIGINAL}"
    const val PATTERN_SEQUENTIAL = "{PREFIX}_{INDEX}"

    data class PresetPattern(
        val pattern: String,
        val labelZh: String,
        val labelEn: String
    )

    val PRESETS = listOf(
        PresetPattern(PATTERN_TIMESTAMP, "时间戳", "Timestamp"),
        PresetPattern(PATTERN_ORIGINAL_PREFIX, "原名保留", "Keep Original"),
        PresetPattern(PATTERN_ALGORITHM_TAGGED, "算法标记", "Algo Tagged"),
        PresetPattern(PATTERN_SEQUENTIAL, "纯序列号", "Sequential")
    )

    /**
     * Extracts the base filename without extension.
     */
    fun getOriginalBaseName(originalFilename: String): String {
        val clean = originalFilename.trim()
        val lastDot = clean.lastIndexOf('.')
        return if (lastDot > 0) clean.substring(0, lastDot) else clean.ifEmpty { "image" }
    }

    /**
     * Sanitizes a filename by replacing illegal filesystem characters with underscores.
     */
    fun sanitizeFilename(name: String): String {
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|\\cA-\\cZ]"), "_")
            .trim()
            .trimStart('.')
        return sanitized.take(120).ifEmpty { "image" }
    }

    /**
     * Generates a concrete filename from template tokens.
     */
    fun generateFilename(
        template: String,
        prefix: String = "RePix",
        originalFilename: String = "image",
        index: Int = 0,
        algorithmName: String = "",
        extension: String = "png",
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val effectiveTemplate = template.ifBlank { PATTERN_TIMESTAMP }
        val effectivePrefix = prefix.ifBlank { "RePix" }
        val dateObj = Date(timestamp)

        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(dateObj)
        val timeStr = SimpleDateFormat("HHmmss", Locale.US).format(dateObj)
        val indexStr = String.format(Locale.US, "%03d", (index + 1).coerceAtLeast(1))
        val baseName = getOriginalBaseName(originalFilename)
        val algoStr = algorithmName.replace(Regex("[^a-zA-Z0-9]"), "")

        var resolved = effectiveTemplate
            .replace(TOKEN_PREFIX, effectivePrefix)
            .replace(TOKEN_ORIGINAL, baseName)
            .replace(TOKEN_DATE, dateStr)
            .replace(TOKEN_TIME, timeStr)
            .replace(TOKEN_INDEX, indexStr)
            .replace(TOKEN_ALGO, algoStr)

        resolved = sanitizeFilename(resolved)
        val cleanExt = extension.removePrefix(".").ifEmpty { "png" }
        return "$resolved.$cleanExt"
    }

    /**
     * Generates a sample filename for UI real-time preview.
     */
    fun formatPreview(
        template: String,
        prefix: String = "RePix",
        sampleOriginalFilename: String = "sample_photo.jpg",
        algorithmName: String = "Tomato",
        extension: String = "png"
    ): String {
        return generateFilename(
            template = template,
            prefix = prefix,
            originalFilename = sampleOriginalFilename,
            index = 0,
            algorithmName = algorithmName,
            extension = extension
        )
    }
}
