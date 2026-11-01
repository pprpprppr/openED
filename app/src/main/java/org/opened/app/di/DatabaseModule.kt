package org.opened.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.opened.app.core.database.OpenEDDatabase
import org.opened.app.core.database.dao.ChapterDao
import org.opened.app.core.database.dao.ChapterLinkDao
import org.opened.app.core.database.dao.ChapterQaDao
import org.opened.app.core.database.dao.ChapterRevisionDao
import org.opened.app.core.database.dao.ModuleDao
import org.opened.app.core.database.dao.StudyPathDao
import org.opened.app.domain.repository.OpenEDRepository
import org.opened.app.domain.repository.OpenEDRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenEDDatabase {
        return Room.databaseBuilder(
            context,
            OpenEDDatabase::class.java,
            OpenEDDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideStudyPathDao(database: OpenEDDatabase): StudyPathDao = database.studyPathDao()

    @Provides
    fun provideModuleDao(database: OpenEDDatabase): ModuleDao = database.moduleDao()

    @Provides
    fun provideChapterDao(database: OpenEDDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideChapterRevisionDao(database: OpenEDDatabase): ChapterRevisionDao = database.chapterRevisionDao()

    @Provides
    fun provideChapterLinkDao(database: OpenEDDatabase): ChapterLinkDao = database.chapterLinkDao()

    @Provides
    fun provideChapterQaDao(database: OpenEDDatabase): ChapterQaDao = database.chapterQaDao()

    @Provides
    @Singleton
    fun provideOpenEDRepository(impl: OpenEDRepositoryImpl): OpenEDRepository = impl
}
