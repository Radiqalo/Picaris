package io.github.pixivnext.core

import android.content.Context
import android.util.Base64
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Dispatcher

class PixivException(val code: Int, message: String) : Exception(message)

@Singleton
class Network @Inject constructor(private val settings: SettingsStore) {
    private var last: Settings? = null
    private var engine: HttpClient? = null
    private var http: OkHttpClient? = null
    private var imageHttp: OkHttpClient? = null
    // Keep image requests queued independently of API calls and downloads.
    private val imageDispatcher = Dispatcher().apply {
        maxRequests = 1
        maxRequestsPerHost = 1
    }
    private val lock = Mutex()

    suspend fun okHttp(): OkHttpClient = lock.withLock {
        configure(settings.flow.first())
        http!!
    }

    suspend fun client(): HttpClient = lock.withLock {
        configure(settings.flow.first())
        engine!!
    }

    private suspend fun imageClient(): OkHttpClient = lock.withLock {
        configure(settings.flow.first())
        imageHttp!!
    }

    private fun configure(s: Settings) {
        if (
            http != null &&
                last?.let {
                    it.proxyType == s.proxyType &&
                        it.proxyHost == s.proxyHost &&
                        it.proxyPort == s.proxyPort
                } == true
        )
            return
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("Referer", "https://www.pixiv.net/")
                            .build()
                    )
                }
        if (s.proxyType != "system")
            builder.proxy(
                if (s.proxyType == "direct") Proxy.NO_PROXY
                else
                    Proxy(
                        if (s.proxyType == "socks") Proxy.Type.SOCKS else Proxy.Type.HTTP,
                        InetSocketAddress(s.proxyHost, s.proxyPort),
                    )
            )
        engine?.close()
        http = builder.build()
        imageHttp = http!!.newBuilder().dispatcher(imageDispatcher).build()
        engine =
            HttpClient(OkHttp) {
                engine { preconfigured = http }
                expectSuccess = false
                followRedirects = false
            }
        last = s
    }

    fun imageLoader(context: Context) =
        ImageLoader.Builder(context)
            .crossfade(200)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { runBlocking { imageClient() } }))
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}

@Singleton
class AuthRepository
@Inject
constructor(private val vault: CredentialStore, private val oauth: OAuthExchange) {
    private val mutex = Mutex()
    private val _data = MutableStateFlow(vault.read())
    val data = _data.asStateFlow()
    val active
        get() = _data.value.accounts.find { it.user.id == _data.value.activeId }

    private fun commit(data: VaultData) {
        vault.write(data)
        _data.value = data
    }

    suspend fun select(id: Long) = mutex.withLock {
        require(data.value.accounts.any { it.user.id == id })
        commit(data.value.copy(activeId = id))
    }

    suspend fun remove(id: Long) = mutex.withLock {
        val accounts = data.value.accounts.filterNot { it.user.id == id }
        commit(
            data.value.copy(
                accounts = accounts,
                activeId =
                    if (active?.user?.id == id) accounts.firstOrNull()?.user?.id
                    else data.value.activeId,
            )
        )
    }

    suspend fun startLogin(): String = mutex.withLock {
        val verifier =
            Base64.encodeToString(
                ByteArray(32).also { SecureRandom().nextBytes(it) },
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        val challenge =
            Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        commit(data.value.copy(pending = AuthSession(verifier, System.currentTimeMillis())))
        "https://app-api.pixiv.net/web/v1/login?code_challenge=$challenge&code_challenge_method=S256&client=pixiv-android"
    }

    suspend fun finishLogin(code: String) = mutex.withLock {
        val session = data.value.pending ?: throw PixivException(400, "登录会话已失效，请重新打开网页登录")
        if (System.currentTimeMillis() - session.createdAt > 15 * 60_000)
            throw PixivException(400, "登录链接已过期，请重新登录")
        val account =
            oauth.exchange(
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "code_verifier" to session.verifier,
                    "redirect_uri" to "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback",
                )
            )
        save(account)
        commit(data.value.copy(pending = null))
    }

    suspend fun importToken(token: String) = mutex.withLock {
        require(token.isNotBlank()) { "请输入 refresh token" }
        save(
            oauth.exchange(mapOf("grant_type" to "refresh_token", "refresh_token" to token.trim()))
        )
    }

    private fun save(account: Account) =
        commit(
            data.value.copy(
                accounts =
                    data.value.accounts.filterNot { it.user.id == account.user.id } + account,
                activeId = account.user.id,
            )
        )

    suspend fun token(accountId: Long, force: Boolean = false, rejected: String? = null): String =
        mutex.withLock {
            val before =
                data.value.accounts.find { it.user.id == accountId }
                    ?: throw PixivException(401, "请登录 Pixiv")
            if (rejected != null && before.accessToken != rejected)
                return@withLock before.accessToken
            if (!force && System.currentTimeMillis() < before.expiresAt - 60_000)
                return@withLock before.accessToken
            val refreshed =
                oauth.exchange(
                    mapOf("grant_type" to "refresh_token", "refresh_token" to before.refreshToken)
                )
            check(refreshed.user.id == accountId) { "刷新凭据的账号不匹配，请重新登录" }
            commit(
                data.value.copy(
                    accounts =
                        data.value.accounts.map { if (it.user.id == accountId) refreshed else it }
                )
            )
            refreshed.accessToken
        }
}

interface OAuthExchange {
    suspend fun exchange(fields: Map<String, String>): Account
}

@Singleton
class PixivOAuth @Inject constructor(private val network: Network) : OAuthExchange {
    override suspend fun exchange(fields: Map<String, String>): Account {
        val response =
            network.client().submitForm(
                "https://oauth.secure.pixiv.net/auth/token",
                Parameters.build {
                    append("client_id", "MOBrBDS8blbauoSck0ZfDbtuzpyT")
                    append("client_secret", "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj")
                    append("get_secure_url", "1")
                    append("include_policy", "true")
                    fields.forEach { (k, v) -> append(k, v) }
                },
            ) {
                header("User-Agent", "PixivAndroidApp/6.170.0 (Android 17; PixivNext)")
            }
        val json = AppJson.parseToJsonElement(response.bodyAsText()).jsonObject
        if (!response.status.isSuccess())
            throw PixivException(response.status.value, "登录失败，请检查登录凭据与网络（${response.status.value}）")
        val root = (json["response"] as? JsonObject) ?: json
        val user = AppJson.decodeFromJsonElement<User>(root.getValue("user"))
        return Account(
            user,
            root.getValue("access_token").jsonPrimitive.content,
            root.getValue("refresh_token").jsonPrimitive.content,
            System.currentTimeMillis() +
                (root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600) * 1000,
            root["user"]?.jsonObject?.get("is_premium")?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }
}

@Singleton
class PixivApi @Inject constructor(private val auth: AuthRepository, private val network: Network) {
    suspend fun get(
        account: Long,
        path: String,
        params: Map<String, String> = emptyMap(),
    ): JsonObject = request(account, path, params, false)

    suspend fun post(account: Long, path: String, fields: Map<String, String>) =
        request(account, path, fields, true)

    suspend fun text(account: Long, path: String, params: Map<String, String>): String {
        var token = auth.token(account)
        repeat(2) { attempt ->
            val response =
                network.client().get("https://app-api.pixiv.net/$path") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.UserAgent, "PixivAndroidApp/6.170.0 (Android 17; PixivNext)")
                    params.forEach { (k, v) -> parameter(k, v) }
                }
            if (response.status.value == 401 && attempt == 0) {
                token = auth.token(account, true, token)
                return@repeat
            }
            if (!response.status.isSuccess())
                throw PixivException(response.status.value, "无法加载小说正文（${response.status.value}）")
            return response.bodyAsText()
        }
        throw PixivException(401, "请重新登录")
    }

    private suspend fun request(
        account: Long,
        path: String,
        values: Map<String, String>,
        post: Boolean,
    ): JsonObject {
        val url = if (path.startsWith("https://")) path else "https://app-api.pixiv.net/$path"
        require(Url(url).host == "app-api.pixiv.net" && Url(url).protocol == URLProtocol.HTTPS) {
            "拒绝无效的分页地址"
        }
        var token = auth.token(account)
        repeat(2) { attempt ->
            val response =
                if (post)
                    network.client().submitForm(
                        url,
                        Parameters.build { values.forEach { (k, v) -> append(k, v) } },
                    ) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header(
                            HttpHeaders.UserAgent,
                            "PixivAndroidApp/6.170.0 (Android 17; PixivNext)",
                        )
                    }
                else
                    network.client().get(url) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header(
                            HttpHeaders.UserAgent,
                            "PixivAndroidApp/6.170.0 (Android 17; PixivNext)",
                        )
                        values.forEach { (k, v) -> parameter(k, v) }
                    }
            if (response.status.value == 401 && attempt == 0) {
                token = auth.token(account, true, token)
                return@repeat
            }
            if (!response.status.isSuccess())
                throw PixivException(
                    response.status.value,
                    when (response.status.value) {
                        401 -> "登录已过期，请重新登录"
                        403 -> "当前账号无法访问此内容"
                        404 -> "作品不存在或已被删除"
                        429 -> "请求过于频繁，请稍后重试"
                        else -> "Pixiv 服务暂时不可用（${response.status.value}）"
                    },
                )
            return AppJson.parseToJsonElement(response.bodyAsText()).jsonObject
        }
        throw PixivException(401, "登录已过期")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides fun credentials(vault: TokenVault): CredentialStore = vault

    @Provides fun oauth(exchange: PixivOAuth): OAuthExchange = exchange

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context) =
        androidx.room.Room.databaseBuilder(context, PixivDatabase::class.java, "pixivnext.db")
            .build()

    @Provides fun library(db: PixivDatabase): LibraryDao = db.library()
}
