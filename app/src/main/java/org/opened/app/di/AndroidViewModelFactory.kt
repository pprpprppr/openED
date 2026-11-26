package org.opened.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.ai.MultiAiClient
import org.opened.app.core.learning.AdaptiveLearningEngine
import org.opened.app.core.platform.ApiKeyStore
import org.opened.app.core.platform.PlatformBackupGateway
import org.opened.app.core.platform.UserPreferencesStore
import org.opened.app.core.sync.SyncManager
import org.opened.app.core.tts.TtsController
import org.opened.app.domain.repository.OpenEDRepository
import org.opened.app.ui.flashcards.FlashcardReviewViewModel
import org.opened.app.ui.graph.KnowledgeGraphViewModel
import org.opened.app.ui.home.HomeViewModel
import org.opened.app.ui.mastery.MasteryViewModel
import org.opened.app.ui.mocktest.MockTestListViewModel
import org.opened.app.ui.mocktest.MockTestResultViewModel
import org.opened.app.ui.mocktest.TakeMockTestViewModel
import org.opened.app.ui.settings.SettingsViewModel
import org.opened.app.ui.wizard.DiagnosticWizardViewModel
import org.opened.app.ui.workspace.CourseWorkspaceViewModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidViewModelFactory @Inject constructor(
    private val repository: OpenEDRepository,
    private val apiKeyStore: ApiKeyStore,
    private val preferencesStore: UserPreferencesStore,
    private val aiLearningEngine: AiLearningEngine,
    private val adaptiveLearningEngine: AdaptiveLearningEngine,
    private val ttsController: TtsController,
    private val backupGateway: PlatformBackupGateway,
    private val multiAiClient: MultiAiClient,
    private val syncManager: SyncManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository, apiKeyStore, preferencesStore) as T
            modelClass.isAssignableFrom(DiagnosticWizardViewModel::class.java) ->
                DiagnosticWizardViewModel(aiLearningEngine, preferencesStore) as T
            modelClass.isAssignableFrom(CourseWorkspaceViewModel::class.java) ->
                CourseWorkspaceViewModel(repository, aiLearningEngine, ttsController, preferencesStore) as T
            modelClass.isAssignableFrom(MockTestListViewModel::class.java) ->
                MockTestListViewModel(repository, aiLearningEngine) as T
            modelClass.isAssignableFrom(TakeMockTestViewModel::class.java) ->
                TakeMockTestViewModel(repository, aiLearningEngine) as T
            modelClass.isAssignableFrom(MockTestResultViewModel::class.java) ->
                MockTestResultViewModel(repository) as T
            modelClass.isAssignableFrom(KnowledgeGraphViewModel::class.java) ->
                KnowledgeGraphViewModel(repository) as T
            modelClass.isAssignableFrom(FlashcardReviewViewModel::class.java) ->
                FlashcardReviewViewModel(repository, aiLearningEngine, adaptiveLearningEngine) as T
            modelClass.isAssignableFrom(MasteryViewModel::class.java) ->
                MasteryViewModel(aiLearningEngine, repository, adaptiveLearningEngine) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(apiKeyStore, preferencesStore, backupGateway, multiAiClient, syncManager, aiLearningEngine) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
