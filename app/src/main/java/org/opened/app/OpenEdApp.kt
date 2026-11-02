package org.opened.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.opened.app.core.security.SecureApiKeyStorage
import javax.inject.Inject

@HiltAndroidApp
class OpenEdApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var secureApiKeyStorage: SecureApiKeyStorage

    override fun onCreate() {
        super.onCreate()
        // Pre-warm encrypted keystore and preferences cache on IO thread
        secureApiKeyStorage.warmUp()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

typealias OpenEDApplication = OpenEdApp
