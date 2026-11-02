package com.example.picencrypt

import com.example.picencrypt.utils.DemoImageGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying [DemoImageGenerator] specifications and metadata.
 */
class DemoImageGeneratorTest {

    @Test
    fun testDemoSampleInfoSpecifications() {
        val info = DemoImageGenerator.getSampleInfo()
        assertEquals("repix_demo_sample.png", info.name)
        assertEquals(600, info.width)
        assertEquals(600, info.height)
        assertNotNull(info.description)
    }
}
