package io.github.radiqalo.picaris.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
private val Context.preferences by preferencesDataStore("settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("settings_v1")
    val flow =
        context.preferences.data.map {
            runCatching { AppJson.decodeFromString<Settings>(it[key] ?: "{}") }
                .getOrDefault(Settings())
        }

    suspend fun update(block: (Settings) -> Settings) {
        context.preferences.edit { p ->
            val before = runCatching {
                AppJson.decodeFromString<Settings>(p[key] ?: "{}")
            }.getOrDefault(Settings())
            p[key] = AppJson.encodeToString(block(before))
        }
    }
}

class CredentialReadException(
    cause: Exception,
) : IllegalStateException("无法读取已保存的登录信息，请重试；原始凭据已保留", cause)

interface CredentialStore {
    /** Returns empty data only for an absent vault; unreadable data throws [CredentialReadException]. */
    fun read(): VaultData

    fun write(data: VaultData)
}

@Singleton
class TokenVault
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : CredentialStore {
        private companion object {
            const val GCM_IV_BYTES = 12
            const val GCM_TAG_BYTES = 16
        }

        private val prefs = context.getSharedPreferences("credential_vault", Context.MODE_PRIVATE)
        private val secret: SecretKey
            get() {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                return (store.getKey("picaris_vault", null) as? SecretKey)
                    ?: KeyGenerator
                        .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                        .apply {
                            init(
                                KeyGenParameterSpec
                                    .Builder(
                                        "picaris_vault",
                                        KeyProperties.PURPOSE_ENCRYPT or
                                            KeyProperties.PURPOSE_DECRYPT,
                                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                    .build(),
                            )
                        }.generateKey()
            }

        // Crypto, preference type and serialization failures all represent an unreadable vault.
        @Suppress("TooGenericExceptionCaught")
        @Synchronized
        override fun read(): VaultData =
            try {
                val payload = prefs.getString("payload", null)
                if (payload == null) {
                    VaultData()
                } else {
                    val blob = Base64.decode(payload, Base64.NO_WRAP)
                    require(
                        blob.size >= GCM_IV_BYTES + GCM_TAG_BYTES,
                    ) { "Invalid credential payload" }
                    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    val existingKey = store.getKey("picaris_vault", null) as? SecretKey
                    checkNotNull(existingKey) { "Credential key unavailable" }
                    val cipher =
                        Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(
                                Cipher.DECRYPT_MODE,
                                existingKey,
                                GCMParameterSpec(128, blob.copyOfRange(0, GCM_IV_BYTES)),
                            )
                        }
                    AppJson.decodeFromString<VaultData>(
                        cipher
                            .doFinal(
                                blob.copyOfRange(GCM_IV_BYTES, blob.size),
                            ).decodeToString(throwOnInvalidSequence = true),
                    )
                }
            } catch (error: Exception) {
                throw CredentialReadException(error)
            }

        @Synchronized
        override fun write(data: VaultData) {
            // Validate existing ciphertext before replacing it, including writes from a fresh instance.
            read()
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret) }
            val blob = cipher.iv + cipher.doFinal(AppJson.encodeToString(data).encodeToByteArray())
            check(
                prefs
                    .edit()
                    .putString(
                        "payload",
                        Base64.encodeToString(blob, Base64.NO_WRAP),
                    ).commit(),
            ) {
                "无法保存登录信息"
            }
        }
    }

@Serializable @Entity(primaryKeys = ["accountId", "key"])
data class CachedFeed(val accountId: Long, val key: String, val json: String, val savedAt: Long)

@Serializable @Entity(primaryKeys = ["accountId", "workId", "kind"])
data class HistoryEntity(
    val accountId: Long,
    val workId: Long,
    val kind: String,
    val json: String,
    val viewedAt: Long,
    val progress: Int = 0,
)

@Serializable @Entity(primaryKeys = ["accountId", "word"])
data class SearchEntity(val accountId: Long, val word: String, val usedAt: Long)

@Serializable @Entity(
    tableName = "downloads",
    indices = [Index(value = ["accountId", "workId", "page", "kind"], unique = true)],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val workId: Long,
    val kind: String,
    val page: Int,
    val title: String,
    val url: String,
    val name: String,
    val status: String = "queued",
    val bytes: Long = 0,
    val total: Long = 0,
    val error: String = "",
    val uri: String = "",
    val etag: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val coverUri: String = "",
    val workJson: String = "",
)

@Serializable @Entity(tableName = "download_folders")
data class DownloadFolder(@PrimaryKey val key: String, val name: String)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM HistoryEntity") suspend fun allHistory(): List<HistoryEntity>
    @Query("SELECT * FROM SearchEntity") suspend fun allSearches(): List<SearchEntity>
    @Query("SELECT * FROM downloads") suspend fun allDownloads(): List<DownloadEntity>
    @Query("SELECT * FROM download_folders") suspend fun allDownloadFolders(): List<DownloadFolder>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreHistory(items: List<HistoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSearches(items: List<SearchEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreDownloads(items: List<DownloadEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreDownloadFolders(items: List<DownloadFolder>)
    @Query("SELECT name FROM download_folders WHERE `key`=:key")
    suspend fun downloadFolder(key: String): String?
    @Query("SELECT * FROM download_folders")
    suspend fun downloadFolders(): List<DownloadFolder>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun rememberDownloadFolder(folder: DownloadFolder)
    @Query("UPDATE downloads SET name=:name WHERE id=:id AND status='running'")
    suspend fun savedDownloadName(id: Long, name: String)
    @Query("UPDATE downloads SET coverUri=:uri WHERE id=:id AND status='running'")
    suspend fun savedDownloadCover(id: Long, uri: String): Int
    @Query("SELECT * FROM downloads WHERE accountId=:account AND status='complete' ORDER BY createdAt DESC")
    suspend fun finishedDownloads(account: Long): List<DownloadEntity>
    @Query("UPDATE downloads SET name=:name,uri=:uri,coverUri=:cover WHERE id=:id AND status='complete'")
    suspend fun relocateDownload(id: Long, name: String, uri: String, cover: String): Int

    @Query(
        """UPDATE downloads SET uri=:uri,coverUri=:cover
        WHERE id=:id AND status='complete' AND uri=:expectedUri AND coverUri=:expectedCover""",
    )
    suspend fun rebindDownloadUri(
        id: Long,
        expectedUri: String,
        expectedCover: String,
        uri: String,
        cover: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cache(feed: CachedFeed)

    @Query("SELECT * FROM CachedFeed WHERE accountId=:account AND `key`=:key")
    suspend fun cached(account: Long, key: String): CachedFeed?

    @Query("SELECT COALESCE(SUM(LENGTH(CAST(json AS BLOB))), 0) FROM CachedFeed WHERE accountId=:account")
    fun cachedFeedBytes(account: Long): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun record(history: HistoryEntity)

    @Query("SELECT * FROM HistoryEntity WHERE accountId=:account ORDER BY viewedAt DESC LIMIT 300")
    fun history(account: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM HistoryEntity WHERE accountId=:account AND workId=:id AND kind=:kind")
    suspend fun historyItem(account: Long, id: Long, kind: String): HistoryEntity?

    @Query("DELETE FROM HistoryEntity WHERE accountId=:account")
    suspend fun clearHistory(account: Long)

    @Query("DELETE FROM CachedFeed WHERE accountId=:account") suspend fun clearCache(account: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun search(item: SearchEntity)

    @Query("SELECT * FROM SearchEntity WHERE accountId=:account ORDER BY usedAt DESC LIMIT 20")
    fun searches(account: Long): Flow<List<SearchEntity>>

    @Query("DELETE FROM SearchEntity WHERE accountId=:account")
    suspend fun clearSearch(account: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun enqueue(item: DownloadEntity): Long

    @Update suspend fun update(item: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE accountId=:account ORDER BY createdAt DESC")
    fun downloads(account: Long): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status='queued' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun queuedDownloads(limit: Int): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE accountId=:account AND workId=:work AND status IN ('queued','running')")
    suspend fun activeDownloadsForWork(account: Long, work: Long): Int

    @Query("SELECT * FROM downloads WHERE id=:id") suspend fun download(id: Long): DownloadEntity?

    @Query(
        "SELECT * FROM downloads WHERE accountId=:account AND workId=:work AND kind=:kind AND page=:page"
    )
    suspend fun existingDownload(
        account: Long,
        work: Long,
        kind: String,
        page: Int,
    ): DownloadEntity?

    @Query(
        "UPDATE downloads SET status='queued',error='',url=:url,workJson=:json,name=COALESCE(:name,name) WHERE id=:id AND status NOT IN ('queued','running')"
    )
    suspend fun requeueDownload(id: Long, url: String, json: String, name: String? = null): Int

    @Query(
        "SELECT * FROM downloads WHERE accountId=:account AND workId=:work AND status='complete' ORDER BY page ASC"
    )
    suspend fun completed(account: Long, work: Long): List<DownloadEntity>

    @Query("UPDATE downloads SET status='running', error='' WHERE id=:id AND status='queued'")
    suspend fun claimDownload(id: Long): Int

    @Query(
        "UPDATE downloads SET bytes=:bytes,total=:total,etag=:etag WHERE id=:id AND status='running'"
    )
    suspend fun progressDownload(id: Long, bytes: Long, total: Long, etag: String): Int

    @Query(
        "UPDATE downloads SET status='complete',bytes=:bytes,total=:bytes,uri=:uri WHERE id=:id AND status='running'"
    )
    suspend fun completeDownload(id: Long, bytes: Long, uri: String): Int

    @Query("UPDATE downloads SET status=:status, error='' WHERE id=:id")
    suspend fun status(id: Long, status: String)

    @Query("UPDATE downloads SET status='failed', error=:error WHERE id=:id AND status='running'")
    suspend fun failDownload(id: Long, error: String): Int

    @Query(
        "UPDATE downloads SET status=:status,error='' WHERE id=:id AND " +
            "((:status='queued' AND status IN ('paused','failed','cancelled')) OR " +
            "(:status='paused' AND status IN ('queued','running')) OR " +
            "(:status='cancelled' AND status IN ('queued','running','paused','failed')))"
    )
    suspend fun changeDownloadStatus(id: Long, status: String): Int

    @Query("UPDATE downloads SET status='queued' WHERE status='running'")
    suspend fun recoverDownloads()

    @Query("UPDATE downloads SET status='queued',error='',bytes=0,total=0,uri='',etag='' WHERE id=:id AND status='complete'")
    suspend fun retryCompletedDownload(id: Long): Int

    @Query("DELETE FROM CachedFeed WHERE savedAt<:before") suspend fun expireCache(before: Long)

    @Query("SELECT id FROM downloads WHERE status IN ('cancelled','complete')")
    suspend fun finishedIds(): List<Long>

    @Query("DELETE FROM downloads WHERE id=:id") suspend fun deleteDownload(id: Long)

    @Query("DELETE FROM downloads WHERE id=:id AND accountId=:account AND status IN ('complete','failed','cancelled')")
    suspend fun deleteFinishedDownload(account: Long, id: Long): Int
}

@Database(
    entities =
        [CachedFeed::class, HistoryEntity::class, SearchEntity::class, DownloadEntity::class, DownloadFolder::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class PixivDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}
