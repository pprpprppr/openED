package org.opened.app.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.opened.app.core.drive.BackupBundle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFileSystemSyncProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncProvider {

    override val providerId: String = "LOCAL"
    override val displayName: String = "Local Device Archive"

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val syncFileName = "opened_sync_bundle.zip"
    private val manifestEntryName = "opened_manifest.json"

    private fun getSyncFile(): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, syncFileName)
    }

    override suspend fun isConfigured(): Boolean = true

    override suspend fun uploadBundle(bundle: BackupBundle): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = getSyncFile()
            val bundleJson = json.encodeToString(BackupBundle.serializer(), bundle)

            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zipOut ->
                zipOut.putNextEntry(ZipEntry(manifestEntryName))
                zipOut.write(bundleJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
            file.writeBytes(bos.toByteArray())
            "local://${file.absolutePath}"
        }
    }

    override suspend fun downloadLatestBundle(): Result<Pair<String, BackupBundle>?> = withContext(Dispatchers.IO) {
        runCatching {
            val file = getSyncFile()
            if (!file.exists()) return@runCatching null

            val zipIn = ZipInputStream(ByteArrayInputStream(file.readBytes()))
            var bundleJson: String? = null
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == manifestEntryName || entry.name.endsWith(".json")) {
                    bundleJson = zipIn.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
                entry = zipIn.nextEntry
            }

            if (bundleJson == null) return@runCatching null
            val bundle = json.decodeFromString(BackupBundle.serializer(), bundleJson)
            file.absolutePath to bundle
        }
    }

    override suspend fun listRevisions(): Result<List<SyncRevisionMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = getSyncFile()
            if (!file.exists()) return@runCatching emptyList()

            listOf(
                SyncRevisionMetadata(
                    revisionId = "local_latest",
                    providerName = displayName,
                    timestamp = file.lastModified(),
                    sizeBytes = file.length(),
                    description = "On-device archive at ${file.name}"
                )
            )
        }
    }
}
