package com.example.picencrypt

import com.example.picencrypt.utils.MiragePresetGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying Mirage demo preset theme definitions and metadata.
 */
class MiragePresetGeneratorTest {

    @Test
    fun testPresetThemesDefinition() {
        val themes = MiragePresetGenerator.PresetTheme.entries
        assertEquals(2, themes.size)
        assertNotNull(MiragePresetGenerator.PresetTheme.valueOf("DAY_AND_NIGHT"))
        assertNotNull(MiragePresetGenerator.PresetTheme.valueOf("QUESTION_AND_TRUTH"))
    }
}
