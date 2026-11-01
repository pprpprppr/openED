package org.opened.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.opened.app.core.database.entity.ChapterLinkCrossRef

@Dao
interface ChapterLinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: ChapterLinkCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<ChapterLinkCrossRef>)

    @Query("DELETE FROM chapter_links WHERE sourceChapterId = :sourceChapterId")
    suspend fun deleteOutgoingLinks(sourceChapterId: String)

    @Query("SELECT * FROM chapter_links WHERE sourceChapterId = :sourceChapterId")
    fun getOutgoingLinksFlow(sourceChapterId: String): Flow<List<ChapterLinkCrossRef>>

    @Query("SELECT * FROM chapter_links WHERE targetChapterId = :targetChapterId")
    fun getBacklinksFlow(targetChapterId: String): Flow<List<ChapterLinkCrossRef>>

    @Query("SELECT * FROM chapter_links")
    suspend fun getAllForExport(): List<ChapterLinkCrossRef>

    @Query("DELETE FROM chapter_links")
    suspend fun clearAll()
}
