package org.opened.app.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    private fun createRecordingDatabase(recordedStatements: MutableList<String>): SupportSQLiteDatabase {
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                recordedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase
    }

    @Test
    fun testMigration3To4ExecutesExpectedSql() {
        val statements = mutableListOf<String>()
        val db = createRecordingDatabase(statements)
        OpenEDDatabase.MIGRATION_3_4.migrate(db)

        val fullScript = statements.joinToString("\n")
        assertTrue("Expected sync_tombstones table creation", fullScript.contains("CREATE TABLE IF NOT EXISTS `sync_tombstones`"))
        assertTrue("Expected chapter status index", fullScript.contains("CREATE INDEX IF NOT EXISTS `index_chapters_status`"))
        assertTrue("Expected flashcards state index", fullScript.contains("CREATE INDEX IF NOT EXISTS `index_flashcards_state`"))
        assertTrue("Expected learning objectives status index", fullScript.contains("CREATE INDEX IF NOT EXISTS `index_learning_objectives_status`"))
    }

    @Test
    fun testMigration2To3ExecutesExpectedSql() {
        val statements = mutableListOf<String>()
        val db = createRecordingDatabase(statements)
        OpenEDDatabase.MIGRATION_2_3.migrate(db)

        val fullScript = statements.joinToString("\n")
        assertTrue("Expected FSRS stability column", fullScript.contains("ALTER TABLE `flashcards` ADD COLUMN `stability`"))
        assertTrue("Expected learning_objectives table", fullScript.contains("CREATE TABLE IF NOT EXISTS `learning_objectives`"))
        assertTrue("Expected mistake_records table", fullScript.contains("CREATE TABLE IF NOT EXISTS `mistake_records`"))
        assertTrue("Expected documents table", fullScript.contains("CREATE TABLE IF NOT EXISTS `documents`"))
        assertTrue("Expected document_chunks table", fullScript.contains("CREATE TABLE IF NOT EXISTS `document_chunks`"))
    }

    @Test
    fun testMigration1To2ExecutesExpectedSql() {
        val statements = mutableListOf<String>()
        val db = createRecordingDatabase(statements)
        OpenEDDatabase.MIGRATION_1_2.migrate(db)

        val fullScript = statements.joinToString("\n")
        assertTrue("Expected flashcards table", fullScript.contains("CREATE TABLE IF NOT EXISTS `flashcards`"))
        assertTrue("Expected mastery_quizzes table", fullScript.contains("CREATE TABLE IF NOT EXISTS `mastery_quizzes`"))
    }
}
