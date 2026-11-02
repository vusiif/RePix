package com.example.picencrypt.utils

/**
 * Diagnostic and summary helper for batch and single image operations.
 */
object BatchMetricsHelper {

    /**
     * Format summary text for batch scramble/unscramble processing.
     */
    fun formatProcessSummary(
        isEncrypt: Boolean,
        successCount: Int,
        failCount: Int,
        durationMs: Long
    ): String {
        val total = successCount + failCount
        val action = if (isEncrypt) "混淆" else "解混淆"
        val clampedDuration = durationMs.coerceAtLeast(0L)
        val avgStr = if (total > 1) " (平均 ${clampedDuration / total}ms/张)" else ""

        return if (failCount == 0) {
            "完成${action}：${successCount} 张 · 耗时 ${clampedDuration}ms$avgStr"
        } else {
            "完成${action}：成功 $successCount，失败 $failCount · 耗时 ${clampedDuration}ms"
        }
    }

    /**
     * Format summary text for batch save processing.
     */
    fun formatSaveSummary(
        successCount: Int,
        failCount: Int,
        durationMs: Long
    ): String {
        val total = successCount + failCount
        val clampedDuration = durationMs.coerceAtLeast(0L)
        val avgStr = if (total > 1) " (平均 ${clampedDuration / total}ms/张)" else ""

        return if (failCount == 0) {
            "保存完成：${successCount} 张 · 耗时 ${clampedDuration}ms$avgStr"
        } else {
            "保存完成：成功 $successCount，失败 $failCount · 耗时 ${clampedDuration}ms"
        }
    }
}
