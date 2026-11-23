package com.example.picencrypt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesConfigTest {

    private fun findMainDir(): File {
        val candidates = listOf(
            File("src/main"),
            File("app/src/main"),
            File("../app/src/main")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Cannot find src/main directory for backup rules test")
    }

    @Test
    fun testAndroidManifestDoesNotEnableLargeHeap() {
        val manifestFile = File(findMainDir(), "AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()
        assertFalse("largeHeap=true must be removed after streaming optimization", content.contains("android:largeHeap=\"true\""))
    }

    @Test
    fun testBackupRulesExcludesSensitiveDataByDefault() {
        val backupRulesFile = File(findMainDir(), "res/xml/backup_rules.xml")
        assertTrue("backup_rules.xml must exist", backupRulesFile.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(backupRulesFile)
        doc.documentElement.normalize()

        assertEqualsRootTag("full-backup-content", doc.documentElement.tagName)

        val includes = doc.getElementsByTagName("include")
        var hasPrefsInclude = false
        for (i in 0 until includes.length) {
            val element = includes.item(i) as Element
            val domain = element.getAttribute("domain")
            val path = element.getAttribute("path")
            if (domain == "sharedpref" && path == "repix_prefs.xml") {
                hasPrefsInclude = true
            }
            // Ensure no sensitive domains are included
            assertFalse("file domain must not be included", domain == "file")
            assertFalse("database domain must not be included", domain == "database")
            assertFalse("external domain must not be included", domain == "external")
            assertFalse("root domain must not be included", domain == "root")
        }
        assertTrue("backup_rules must explicitly include only repix_prefs.xml", hasPrefsInclude)
    }

    @Test
    fun testDataExtractionRulesExcludesSensitiveDataByDefault() {
        val dataExtractionRulesFile = File(findMainDir(), "res/xml/data_extraction_rules.xml")
        assertTrue("data_extraction_rules.xml must exist", dataExtractionRulesFile.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(dataExtractionRulesFile)
        doc.documentElement.normalize()

        assertEqualsRootTag("data-extraction-rules", doc.documentElement.tagName)

        val cloudBackupNodes = doc.getElementsByTagName("cloud-backup")
        assertTrue("cloud-backup element must be present", cloudBackupNodes.length > 0)
        val cloudBackup = cloudBackupNodes.item(0) as Element
        val cloudIncludes = cloudBackup.getElementsByTagName("include")
        var cloudHasPrefs = false
        for (i in 0 until cloudIncludes.length) {
            val el = cloudIncludes.item(i) as Element
            val domain = el.getAttribute("domain")
            val path = el.getAttribute("path")
            if (domain == "sharedpref" && path == "repix_prefs.xml") {
                cloudHasPrefs = true
            }
            assertFalse("cloud-backup must not include file domain", domain == "file")
            assertFalse("cloud-backup must not include database domain", domain == "database")
            assertFalse("cloud-backup must not include root domain", domain == "root")
        }
        assertTrue("cloud-backup must include repix_prefs.xml", cloudHasPrefs)

        val deviceTransferNodes = doc.getElementsByTagName("device-transfer")
        assertTrue("device-transfer element must be present", deviceTransferNodes.length > 0)
        val deviceTransfer = deviceTransferNodes.item(0) as Element
        val transferIncludes = deviceTransfer.getElementsByTagName("include")
        var transferHasPrefs = false
        for (i in 0 until transferIncludes.length) {
            val el = transferIncludes.item(i) as Element
            val domain = el.getAttribute("domain")
            val path = el.getAttribute("path")
            if (domain == "sharedpref" && path == "repix_prefs.xml") {
                transferHasPrefs = true
            }
            assertFalse("device-transfer must not include file domain", domain == "file")
            assertFalse("device-transfer must not include database domain", domain == "database")
            assertFalse("device-transfer must not include root domain", domain == "root")
        }
        assertTrue("device-transfer must include repix_prefs.xml", transferHasPrefs)
    }

    private fun assertEqualsRootTag(expected: String, actual: String) {
        assertTrue("Expected root tag <$expected> but was <$actual>", expected.equals(actual, ignoreCase = true))
    }
}
