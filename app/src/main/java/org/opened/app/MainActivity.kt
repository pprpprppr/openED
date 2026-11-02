package org.opened.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.opened.app.core.datastore.UserPreferences
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.worker.BackupScheduler
import org.opened.app.ui.navigation.OpenEDNavHost
import org.opened.app.ui.theme.OpenEDTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var backupScheduler: BackupScheduler

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure periodic backup job is registered
        backupScheduler.schedulePeriodicBackup()

        setContent {
            val userPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())

            OpenEDTheme(
                highContrast = userPrefs.highContrastEnabled,
                reducedMotion = userPrefs.reducedMotionEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    OpenEDNavHost(navController = navController)
                }
            }
        }
    }
}
