package org.opened.app.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.opened.app.core.datastore.UserPreferencesRepository
import org.opened.app.core.drive.BackupBundle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class WebDavSyncProvider @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val okHttpClient: OkHttpClient
) : SyncProvider {

    override val providerId: String = "WEBDAV"
    override val displayName: String = "WebDAV / Nextcloud"

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val syncFileName = "opened_webdav_bundle.zip"
    private val manifestEntryName = "opened_manifest.json"

    override suspend fun isConfigured(): Boolean {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        return prefs.webDavServerUrl.isNotBlank()
    }

    override suspend fun uploadBundle(bundle: BackupBundle): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val serverUrl = prefs.webDavServerUrl.trimEnd('/')
            if (serverUrl.isBlank()) throw IllegalStateException("WebDAV server URL not configured")

            val bundleJson = json.encodeToString(BackupBundle.serializer(), bundle)
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zipOut ->
                zipOut.putNextEntry(ZipEntry(manifestEntryName))
                zipOut.write(bundleJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
            val zipBytes = bos.toByteArray()

            val targetUrl = "$serverUrl/$syncFileName"
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .put(zipBytes.toRequestBody("application/zip".toMediaType()))

            if (prefs.webDavUsername.isNotBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(prefs.webDavUsername, ""))
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                    throw IllegalStateException("WebDAV upload failed: HTTP ${response.code} ${response.message}")
                }
            }
            targetUrl
        }
    }

    override suspend fun downloadLatestBundle(): Result<Pair<String, BackupBundle>?> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val serverUrl = prefs.webDavServerUrl.trimEnd('/')
            if (serverUrl.isBlank()) return@runCatching null

            val targetUrl = "$serverUrl/$syncFileName"
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .get()

            if (prefs.webDavUsername.isNotBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(prefs.webDavUsername, ""))
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 404) return@runCatching null
                if (!response.isSuccessful) {
                    throw IllegalStateException("WebDAV download failed: HTTP ${response.code} ${response.message}")
                }
                val bodyBytes = response.body?.bytes() ?: return@runCatching null

                val zipIn = ZipInputStream(ByteArrayInputStream(bodyBytes))
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
                targetUrl to bundle
            }
        }
    }

    override suspend fun listRevisions(): Result<List<SyncRevisionMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConfigured()) return@runCatching emptyList()
            listOf(
                SyncRevisionMetadata(
                    revisionId = "webdav_latest",
                    providerName = displayName,
                    timestamp = System.currentTimeMillis(),
                    sizeBytes = 0L,
                    description = "Remote WebDAV / Nextcloud Bundle"
                )
            )
        }
    }
}
