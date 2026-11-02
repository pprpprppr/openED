package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.DocumentChunkEntity
import org.opened.app.core.database.entity.DocumentEntity

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity)

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocumentsFlow(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    // Document Chunks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("SELECT * FROM document_chunks WHERE documentId = :documentId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDocument(documentId: String): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks WHERE documentId IN (:documentIds)")
    suspend fun getChunksForDocuments(documentIds: List<String>): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks WHERE rawText LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchChunksByText(query: String, limit: Int = 20): List<DocumentChunkEntity>

    @Query("DELETE FROM document_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksForDocument(documentId: String)
}
