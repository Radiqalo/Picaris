package io.github.pixivnext.core

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

interface CredentialStore {
    fun read(): VaultData

    fun write(data: VaultData)
}

@Singleton
class TokenVault @Inject constructor(@ApplicationContext context: Context) : CredentialStore {
    private val prefs = context.getSharedPreferences("credential_vault", Context.MODE_PRIVATE)
    private val secret: SecretKey
        get() {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            return (store.getKey("pixivnext_vault", null) as? SecretKey)
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    .apply {
                        init(
                            KeyGenParameterSpec.Builder(
                                    "pixivnext_vault",
                                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                                )
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .build()
                        )
                    }
                    .generateKey()
        }

    override fun read(): VaultData = runCatching {
        val blob =
            Base64.decode(prefs.getString("payload", null) ?: return VaultData(), Base64.NO_WRAP)
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            }
        AppJson.decodeFromString<VaultData>(
            cipher.doFinal(blob.copyOfRange(12, blob.size)).decodeToString()
        )
    }
        .getOrDefault(VaultData())

    override fun write(data: VaultData) {
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret) }
        val blob = cipher.iv + cipher.doFinal(AppJson.encodeToString(data).encodeToByteArray())
        check(
            prefs.edit().putString("payload", Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
        ) {
            "无法保存登录信息"
        }
    }
}

@Entity(primaryKeys = ["accountId", "key"])
data class CachedFeed(val accountId: Long, val key: String, val json: String, val savedAt: Long)

@Entity(primaryKeys = ["accountId", "workId", "kind"])
data class HistoryEntity(
    val accountId: Long,
    val workId: Long,
    val kind: String,
    val json: String,
    val viewedAt: Long,
    val progress: Int = 0,
)

@Entity(primaryKeys = ["accountId", "word"])
data class SearchEntity(val accountId: Long, val word: String, val usedAt: Long)

@Entity(
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
    val workJson: String = "",
)

@Dao
interface LibraryDao {
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

    @Query("SELECT * FROM downloads WHERE status='queued' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextDownload(): DownloadEntity?

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
        "UPDATE downloads SET status='queued',error='',url=:url,workJson=:json WHERE id=:id AND status NOT IN ('queued','running')"
    )
    suspend fun requeueDownload(id: Long, url: String, json: String): Int

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

    @Query("DELETE FROM CachedFeed WHERE savedAt<:before") suspend fun expireCache(before: Long)

    @Query("SELECT id FROM downloads WHERE status IN ('cancelled','complete')")
    suspend fun finishedIds(): List<Long>

    @Query("DELETE FROM downloads WHERE id=:id") suspend fun deleteDownload(id: Long)

    @Query("DELETE FROM downloads WHERE id=:id AND accountId=:account AND status IN ('complete','failed','cancelled')")
    suspend fun deleteFinishedDownload(account: Long, id: Long): Int
}

@Database(
    entities =
        [CachedFeed::class, HistoryEntity::class, SearchEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PixivDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}
