package com.example.picencrypt

import com.example.picencrypt.utils.BatchMetricsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BatchMetricsHelper].
 */
class BatchMetricsHelperTest {

    @Test
    fun testProcessSummarySingleSuccess() {
        val summary = BatchMetricsHelper.formatProcessSummary(
            isEncrypt = true,
            successCount = 1,
            failCount = 0,
            durationMs = 45L
        )
        assertEquals("完成混淆：1 张 · 耗时 45ms", summary)
    }

    @Test
    fun testProcessSummaryMultipleSuccess() {
        val summary = BatchMetricsHelper.formatProcessSummary(
            isEncrypt = true,
            successCount = 10,
            failCount = 0,
            durationMs = 500L
        )
        assertEquals("完成混淆：10 张 · 耗时 500ms (平均 50ms/张)", summary)
    }

    @Test
    fun testProcessSummaryDecrypt() {
        val summary = BatchMetricsHelper.formatProcessSummary(
            isEncrypt = false,
            successCount = 4,
            failCount = 0,
            durationMs = 120L
        )
        assertEquals("完成解混淆：4 张 · 耗时 120ms (平均 30ms/张)", summary)
    }

    @Test
    fun testProcessSummaryWithFailures() {
        val summary = BatchMetricsHelper.formatProcessSummary(
            isEncrypt = true,
            successCount = 8,
            failCount = 2,
            durationMs = 640L
        )
        assertEquals("完成混淆：成功 8，失败 2 · 耗时 640ms", summary)
    }

    @Test
    fun testSaveSummarySuccess() {
        val summary = BatchMetricsHelper.formatSaveSummary(
            successCount = 5,
            failCount = 0,
            durationMs = 250L
        )
        assertEquals("保存完成：5 张 · 耗时 250ms (平均 50ms/张)", summary)
    }

    @Test
    fun testSaveSummaryWithFailures() {
        val summary = BatchMetricsHelper.formatSaveSummary(
            successCount = 3,
            failCount = 1,
            durationMs = 180L
        )
        assertEquals("保存完成：成功 3，失败 1 · 耗时 180ms", summary)
    }
}
