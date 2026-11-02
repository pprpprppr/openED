package org.opened.app.core.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertFalse("Must not expose <root-path>", content.contains("<root-path"))

        // 2. Must NOT expose broad path="."
        assertFalse("Must not expose broad path=\".\"", content.contains("path=\".\""))

        // 3. Must NOT expose arbitrary external storage
        assertFalse("Must not expose external-cache-path", content.contains("<external-cache-path"))
        assertFalse("Must not expose external-files-path", content.contains("<external-files-path"))

        // 4. Must expose only dedicated export paths
        assertTrue("Must contain dedicated exports cache-path", content.contains("path=\"exports/\""))
    }
}
