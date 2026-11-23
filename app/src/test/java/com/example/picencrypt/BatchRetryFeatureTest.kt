package com.example.picencrypt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRetryFeatureTest {

    @Test
    fun testFailedTrackingAndRetryLogic() {
        // Mock a collection of item IDs
        val allItemIds = listOf("item-1", "item-2", "item-3", "item-4", "item-5")
        val failedIds = mutableListOf<String>()

        // Simulate first batch run where item-2 and item-4 fail
        for (id in allItemIds) {
            val isSuccess = id != "item-2" && id != "item-4"
            if (!isSuccess) {
                failedIds.add(id)
            }
        }

        assertEquals(2, failedIds.size)
        assertTrue(failedIds.contains("item-2"))
        assertTrue(failedIds.contains("item-4"))

        // Simulate retry only for failed items
        val newlySucceeded = mutableListOf<String>()
        for (id in failedIds) {
            // Assume item-2 succeeds on retry, while item-4 still fails
            val retrySuccess = id == "item-2"
            if (retrySuccess) {
                newlySucceeded.add(id)
            }
        }

        failedIds.removeAll(newlySucceeded)
        assertEquals(1, failedIds.size)
        assertFalse("item-2 should be removed from failed list after successful retry", failedIds.contains("item-2"))
        assertTrue("item-4 should remain in failed list", failedIds.contains("item-4"))
    }
}
