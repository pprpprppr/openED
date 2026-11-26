package org.opened.desktop

import java.io.File

object DesktopPaths {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    val dataDir: File by lazy {
        val base = if (isWindows) {
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: File(System.getProperty("user.home"), "AppData/Roaming")
        } else {
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: File(System.getProperty("user.home"), ".local/share")
        }
        File(base, "openED").apply { mkdirs() }
    }

    val databaseFile: File by lazy {
        File(dataDir, "opened_database.db")
    }

    val preferencesFile: File by lazy {
        File(dataDir, "opened_user_preferences.preferences_pb")
    }

    val vaultFile: File by lazy {
        File(dataDir, "opened_keystore_vault.properties")
    }

    val vaultKeyFile: File by lazy {
        File(dataDir, "opened_master.key")
    }

    val exportsDir: File by lazy {
        File(dataDir, "exports").apply { mkdirs() }
    }

    val backupsDir: File by lazy {
        File(dataDir, "backups").apply { mkdirs() }
    }
}
