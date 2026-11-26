package org.opened.desktop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.opened.app.core.ai.AiLearningEngine
import org.opened.app.core.ai.GeminiApiClient
import org.opened.app.core.ai.MultiAiClient
import org.opened.app.core.ai.provider.AiCapabilityRegistry
import org.opened.app.core.ai.provider.AnthropicProvider
import org.opened.app.core.ai.provider.GeminiProvider
import org.opened.app.core.ai.provider.OllamaCompatibleProvider
import org.opened.app.core.ai.provider.OpenAiProvider
import org.opened.app.core.ai.provider.TokenUsageTracker
import org.opened.app.core.learning.AdaptiveLearningEngine
import org.opened.app.core.sync.SyncManager
import org.opened.app.core.sync.WebDavSyncProvider
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
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class DesktopAppContainer {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val apiKeyStorage = DesktopApiKeyStorage()
    val preferencesStore = DesktopUserPreferencesRepository()
    val repository = DesktopOpenEDRepository()
    val ttsManager = DesktopTtsManager()

    private val tokenUsageTracker = TokenUsageTracker()
    private val geminiApiClient = GeminiApiClient(okHttpClient, apiKeyStorage)
    private val capabilityRegistry = AiCapabilityRegistry(
        geminiProvider = GeminiProvider(geminiApiClient, okHttpClient, apiKeyStorage, tokenUsageTracker),
        anthropicProvider = AnthropicProvider(okHttpClient, apiKeyStorage, tokenUsageTracker),
        openAiProvider = OpenAiProvider(okHttpClient, apiKeyStorage, tokenUsageTracker),
        ollamaCompatibleProvider = OllamaCompatibleProvider(okHttpClient, apiKeyStorage, tokenUsageTracker),
        tokenUsageTracker = tokenUsageTracker
    )
    val multiAiClient = MultiAiClient(capabilityRegistry, apiKeyStorage, preferencesStore)
    val aiLearningEngine = AiLearningEngine(multiAiClient, repository, preferencesStore)
    val adaptiveLearningEngine = AdaptiveLearningEngine(repository, multiAiClient, preferencesStore)
    val backupGateway = DesktopBackupGateway(repository, preferencesStore)

    private val localSyncProvider = DesktopFileSystemSyncProvider(preferencesStore)
    private val webDavSyncProvider = WebDavSyncProvider(preferencesStore, okHttpClient)
    val syncManager = SyncManager(
        preferencesRepository = preferencesStore,
        repository = repository,
        providers = listOf(localSyncProvider, webDavSyncProvider)
    )

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            return when (modelClass) {
                HomeViewModel::class -> HomeViewModel(repository, apiKeyStorage, preferencesStore) as T
                DiagnosticWizardViewModel::class -> DiagnosticWizardViewModel(aiLearningEngine, preferencesStore) as T
                CourseWorkspaceViewModel::class -> CourseWorkspaceViewModel(repository, aiLearningEngine, ttsManager, preferencesStore) as T
                MockTestListViewModel::class -> MockTestListViewModel(repository, aiLearningEngine) as T
                TakeMockTestViewModel::class -> TakeMockTestViewModel(repository, aiLearningEngine) as T
                MockTestResultViewModel::class -> MockTestResultViewModel(repository) as T
                KnowledgeGraphViewModel::class -> KnowledgeGraphViewModel(repository) as T
                FlashcardReviewViewModel::class -> FlashcardReviewViewModel(repository, aiLearningEngine, adaptiveLearningEngine) as T
                MasteryViewModel::class -> MasteryViewModel(aiLearningEngine, repository, adaptiveLearningEngine) as T
                SettingsViewModel::class -> SettingsViewModel(apiKeyStorage, preferencesStore, backupGateway, multiAiClient, syncManager, aiLearningEngine) as T
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.qualifiedName}")
            }
        }
    }
}
