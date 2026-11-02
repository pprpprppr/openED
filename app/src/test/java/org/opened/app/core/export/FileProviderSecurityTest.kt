package org.opened.app.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class FileProviderSecurityTest {

    @Test
    fun testFileProviderPathsXmlDoesNotExposeRootOrBroadPaths() {
        val xmlFile = File("src/main/res/xml/file_provider_paths.xml")
        val altXmlFile = File("app/src/main/res/xml/file_provider_paths.xml")
        val targetFile = if (xmlFile.exists()) xmlFile else altXmlFile

        assertTrue("file_provider_paths.xml must exist", targetFile.exists())
        val content = targetFile.readText()

        // 1. Must NOT contain root-path
        assertFalse("Must not expose <root-path", content.contains("<root-path ") || content.contains("<root-path/"))

        // 2. Must NOT expose broad path="."
        assertFalse("Must not expose broad path=\".\"", content.contains("path=\".\""))

        // 3. Must NOT expose arbitrary external storage
        assertFalse("Must not expose external-path", content.contains("<external-path"))
        assertFalse("Must not expose external-cache-path", content.contains("<external-cache-path"))
        assertFalse("Must not expose external-files-path", content.contains("<external-files-path"))
        assertFalse("Must not expose external-media-path", content.contains("<external-media-path"))

        // 4. Must expose only dedicated files/exports/ directory
        assertTrue("Must contain dedicated exports files-path", content.contains("<files-path name=\"exports\" path=\"exports/\""))
    }

    @Test
    fun testFileProviderPathTraversalRejectionOutsideExports() {
        val baseExportsDir = File("/data/user/0/org.opened.app/files/exports").canonicalFile
        val canonicalBasePath = baseExportsDir.path

        // Simulated malicious path traversal inputs attempting to access internal databases or prefs
        val dangerousFileNames = listOf(
            "../databases/opened.db",
            "../shared_prefs/opened_keystore_vault.xml",
            "../../shared_prefs/opened_secure_vault.xml",
            "../../../etc/passwd",
            "subfolder/../../databases/opened.db",
            java.net.URLDecoder.decode("..%2Fdatabases%2Fopened.db", "UTF-8")
        )

        for (maliciousName in dangerousFileNames) {
            val resolvedFile = File(baseExportsDir, maliciousName).canonicalFile
            val isWithinPermittedDirectory = resolvedFile.path.startsWith(canonicalBasePath + File.separator)

            assertFalse(
                "Path traversal '$maliciousName' must NOT resolve within exports directory! Resolved to: ${resolvedFile.path}",
                isWithinPermittedDirectory
            )
        }

        // Legitimate exports must resolve strictly within canonicalBasePath
        val legitimateExport = File(baseExportsDir, "openED_obsidian_vault.zip").canonicalFile
        assertTrue(
            "Legitimate export file must resolve within permitted exports directory",
            legitimateExport.path.startsWith(canonicalBasePath + File.separator)
        )
    }
}
