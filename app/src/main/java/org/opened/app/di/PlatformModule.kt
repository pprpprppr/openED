package org.opened.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.platform.ApiKeyStore
import org.opened.app.core.platform.PlatformBackupGateway
import org.opened.app.core.platform.UserPreferencesStore
import org.opened.app.core.security.SecureApiKeyStorage
import org.opened.app.core.sync.GoogleDriveSyncProvider
import org.opened.app.core.sync.LocalFileSystemSyncProvider
import org.opened.app.core.sync.SyncManager
import org.opened.app.core.sync.WebDavSyncProvider
import org.opened.app.core.tts.TtsController
import org.opened.app.core.tts.TtsManager
import org.opened.app.domain.repository.OpenEDRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformBindingsModule {

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(impl: SecureApiKeyStorage): ApiKeyStore

    @Binds
    @Singleton
    abstract fun bindUserPreferencesStore(impl: UserPreferencesRepository): UserPreferencesStore

    @Binds
    @Singleton
    abstract fun bindTtsController(impl: TtsManager): TtsController

    @Binds
    @Singleton
    abstract fun bindPlatformBackupGateway(impl: AndroidBackupGateway): PlatformBackupGateway
}

@Module
@InstallIn(SingletonComponent::class)
object SyncProvidersModule {

    @Provides
    @Singleton
    fun provideSyncManager(
        preferencesStore: UserPreferencesStore,
        repository: OpenEDRepository,
        localProvider: LocalFileSystemSyncProvider,
        googleDriveProvider: GoogleDriveSyncProvider,
        webDavProvider: WebDavSyncProvider
    ): SyncManager {
        return SyncManager(
            preferencesRepository = preferencesStore,
            repository = repository,
            providers = listOf(localProvider, googleDriveProvider, webDavProvider)
        )
    }
}
