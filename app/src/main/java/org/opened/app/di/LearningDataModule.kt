package org.opened.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.opened.app.learning.data.dao.ChapterDao as LearningChapterDao
import org.opened.app.learning.data.dao.ContentBlockDao
import org.opened.app.learning.data.dao.LearningPathDao
import org.opened.app.learning.data.db.LearningDatabase
import org.opened.app.learning.domain.LearningRepository
import org.opened.app.learning.domain.LearningRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LearningDataModule {

    @Provides
    @Singleton
    fun provideLearningDatabase(@ApplicationContext context: Context): LearningDatabase {
        return Room.databaseBuilder(
            context,
            LearningDatabase::class.java,
            LearningDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideLearningPathDao(database: LearningDatabase): LearningPathDao {
        return database.learningPathDao()
    }

    @Provides
    fun provideLearningChapterDao(database: LearningDatabase): LearningChapterDao {
        return database.chapterDao()
    }

    @Provides
    fun provideContentBlockDao(database: LearningDatabase): ContentBlockDao {
        return database.contentBlockDao()
    }

    @Provides
    @Singleton
    fun provideLearningRepository(impl: LearningRepositoryImpl): LearningRepository {
        return impl
    }
}
