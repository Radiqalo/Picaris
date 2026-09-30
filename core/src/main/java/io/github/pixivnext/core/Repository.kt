package io.github.pixivnext.core

import androidx.paging.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

@Singleton
class WorkRepository
@Inject
constructor(
    val api: PixivApi,
    val auth: AuthRepository,
    val dao: LibraryDao,
    val settings: SettingsStore,
) {
    fun feed(
        account: Long,
        spec: FeedSpec,
        filter: ContentFilter? = null,
    ): Flow<PagingData<Work>> =
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                object : PagingSource<String, Work>() {
                    override fun getRefreshKey(state: PagingState<String, Work>): String? = null

                    override suspend fun load(
                        params: LoadParams<String>
                    ): LoadResult<String, Work> =
                        try {
                            val (path, query) = endpoint(spec, account)
                            val key = params.key ?: path + query.toSortedMap().toString()
                            val response =
                                try {
                                    val raw =
                                        api.get(
                                            account,
                                            params.key ?: path,
                                            if (params.key == null) query else emptyMap(),
                                        )
                                    val parsed =
                                        AppJson.decodeFromJsonElement<FeedResponse>(raw)
                                    dao.cache(
                                        CachedFeed(
                                            account,
                                            key,
                                            AppJson.encodeToString(parsed),
                                            System.currentTimeMillis(),
                                        )
                                    )
                                    parsed
                                } catch (e: Exception) {
                                    if (
                                        e is PixivException ||
                                            e is kotlinx.coroutines.CancellationException
                                    )
                                        throw e
                                    dao.cached(account, key)?.let {
                                        AppJson.decodeFromString<FeedResponse>(it.json)
                                            .copy(next_url = null)
                                    } ?: throw e
                                }
                            val s = filter ?: settings.flow.first().contentFilter()
                            LoadResult.Page(
                                (response.illusts +
                                        response.novels.map { it.copy(type = "novel") })
                                    .filter(s::allows),
                                null,
                                response.next_url,
                            )
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            LoadResult.Error(e)
                        }
                }
            }
            .flow

    internal fun endpoint(s: FeedSpec, account: Long): Pair<String, Map<String, String>> {
        val kind = if (s.kind == "novel") "novel" else "illust"
        val q = mutableMapOf("filter" to "for_android")
        val path =
            when (s.section) {
                "ranking" -> {
                    q["mode"] = s.mode
                    if (s.date.isNotEmpty()) q["date"] = s.date
                    "v1/$kind/ranking"
                }
                "follow" -> {
                    q["restrict"] = s.restrict
                    if (kind == "novel") "v1/novel/follow" else "v2/illust/follow"
                }
                "bookmarks" -> {
                    q["user_id"] = (s.userId.takeIf { it > 0 } ?: account).toString()
                    q["restrict"] = s.restrict
                    if (s.word.isNotEmpty()) q["tag"] = s.word
                    "v1/user/bookmarks/$kind"
                }
                "user" -> {
                    q["user_id"] = s.userId.toString()
                    if (kind == "illust") q["type"] = if (s.kind == "manga") "manga" else "illust"
                    "v1/user/${if(kind=="novel") "novels" else "illusts"}"
                }
                "series" -> {
                    q["series_id"] = s.userId.toString()
                    "v2/novel/series"
                }
                "related" -> {
                    q["illust_id"] = s.userId.toString()
                    "v2/illust/related"
                }
                "search" -> {
                    q["word"] = s.word
                    q["sort"] = s.sort
                    q["search_target"] =
                        if (kind == "novel" && s.target == "title_and_caption") "text" else s.target
                    q["include_translated_tag_results"] = "true"
                    if (s.startDate.isNotEmpty()) q["start_date"] = s.startDate
                    if (s.endDate.isNotEmpty()) q["end_date"] = s.endDate
                    "v1/search/$kind"
                }
                else -> {
                    if (kind == "novel") {
                        q["include_ranking_novels"] = "false"
                        q["include_ranking_label"] = "true"
                    } else q["include_ranking_illusts"] = "true"
                    q["include_privacy_policy"] = "true"
                    "v1/$kind/recommended"
                }
            }
        return path to q
    }

    suspend fun detail(account: Long, id: Long, novel: Boolean = false): Work {
        val raw =
            api.get(
                account,
                if (novel) "v2/novel/detail" else "v1/illust/detail",
                mapOf((if (novel) "novel_id" else "illust_id") to id.toString()),
            )
        return AppJson.decodeFromJsonElement<Work>(raw.getValue(if (novel) "novel" else "illust"))
            .let { if (novel) it.copy(type = "novel") else it }
    }

    suspend fun record(account: Long, work: Work, progress: Int? = null) {
        val old = dao.historyItem(account, work.id, work.type)
        dao.record(
            HistoryEntity(
                account,
                work.id,
                work.type,
                AppJson.encodeToString(work),
                System.currentTimeMillis(),
                progress ?: old?.progress ?: 0,
            )
        )
    }

    suspend fun bookmark(account: Long, work: Work, public: Boolean = true): Work {
        api.post(
            account,
            "${if(work.is_bookmarked) "v1" else "v2"}/${if(work.isNovel) "novel" else "illust"}/bookmark/${if(work.is_bookmarked) "delete" else "add"}",
            mapOf(
                (if (work.isNovel) "novel_id" else "illust_id") to work.id.toString(),
                "restrict" to if (public) "public" else "private",
            ),
        )
        return work.copy(
            is_bookmarked = !work.is_bookmarked,
            total_bookmarks =
                (work.total_bookmarks + if (work.is_bookmarked) -1 else 1).coerceAtLeast(0),
        )
    }

    suspend fun follow(account: Long, user: User): User {
        api.post(
            account,
            "v1/user/follow/${if(user.is_followed) "delete" else "add"}",
            mapOf("user_id" to user.id.toString(), "restrict" to "public"),
        )
        return user.copy(is_followed = !user.is_followed)
    }

    suspend fun authorDetails(account: Long, id: Long): AuthorDetails =
        AppJson.decodeFromJsonElement(api.get(account, "v1/user/detail", mapOf("user_id" to id.toString())))

    suspend fun user(account: Long, id: Long): Pair<User, String> {
        val j = api.get(account, "v1/user/detail", mapOf("user_id" to id.toString()))
        return AppJson.decodeFromJsonElement<User>(j.getValue("user")) to
            (j["profile"]?.jsonObject?.get("total_illusts")?.jsonPrimitive?.content ?: "")
    }

    suspend fun recommendedAuthors(account: Long): List<UserPreview> {
        val filter = settings.flow.first().contentFilter()
        return AppJson.decodeFromJsonElement<UserResponse>(
            api.get(account, "v1/user/recommended", mapOf("filter" to "for_android"))
        ).user_previews.map { preview ->
            preview.copy(illusts = preview.illusts.filter(filter::allows).map { work ->
                if (work.user.id == 0L) work.copy(user = preview.user) else work
            })
        }.filter { it.illusts.isNotEmpty() }.take(10)
    }

    suspend fun searchUsers(account: Long, word: String): List<User> =
        AppJson.decodeFromJsonElement<UserResponse>(
                api.get(account, "v1/search/user", mapOf("word" to word))
            )
            .user_previews
            .map { it.user }

    fun people(
        account: Long,
        section: String,
        restrict: String,
    ): Flow<PagingData<User>> {
        require(section in setOf("following", "follower", "mypixiv"))
        return Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                object : PagingSource<String, User>() {
                    override fun getRefreshKey(state: PagingState<String, User>): String? = null

                    override suspend fun load(
                        params: LoadParams<String>
                    ): LoadResult<String, User> =
                        try {
                            val query = mutableMapOf("user_id" to account.toString())
                            if (section == "following") query["restrict"] = restrict
                            val response =
                                AppJson.decodeFromJsonElement<UserResponse>(
                                    api.get(
                                        account,
                                        params.key ?: "v1/user/$section",
                                        if (params.key == null) query else emptyMap(),
                                    )
                                )
                            LoadResult.Page(
                                response.user_previews.map { it.user },
                                null,
                                response.next_url,
                            )
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            LoadResult.Error(e)
                        }
                }
            }
            .flow
    }

    suspend fun comments(
        account: Long,
        work: Work,
        parentId: Long? = null,
        next: String? = null,
    ): CommentsResponse {
        val (path, query) = commentEndpoint(work, parentId)
        return AppJson.decodeFromJsonElement<CommentsResponse>(
            api.get(account, next ?: path, if (next == null) query else emptyMap())
        )
    }

    suspend fun postComment(
        account: Long,
        work: Work,
        text: String,
        parentId: Long? = null,
    ): Comment {
        val content = text.trim()
        require(content.isNotEmpty() && content.codePointCount(0, content.length) <= 140) {
            "请输入 1–140 字的评论"
        }
        val kind = if (work.isNovel) "novel" else "illust"
        val fields = mutableMapOf("${kind}_id" to work.id.toString(), "comment" to content)
        parentId?.let { fields["parent_comment_id"] = it.toString() }
        val response = api.post(account, "v1/$kind/comment/add", fields)
        return AppJson.decodeFromJsonElement<Comment>(response.getValue("comment"))
    }

    suspend fun tags(account: Long): List<Tag> =
        api.get(account, "v1/trending-tags/illust", mapOf("filter" to "for_android"))["trend_tags"]
            ?.jsonArray
            ?.map {
                Tag(
                    it.jsonObject["tag"]?.jsonPrimitive?.content ?: "",
                    it.jsonObject["translated_name"]?.jsonPrimitive?.contentOrNull,
                )
            } ?: emptyList()

    suspend fun trendingTags(account: Long): List<TrendingTag> {
        val filter = settings.flow.first().contentFilter()
        return api.get(account, "v1/trending-tags/illust", mapOf("filter" to "for_android"))["trend_tags"]
            ?.jsonArray?.mapNotNull { item ->
                val data = item.jsonObject
                val name = data["tag"]?.jsonPrimitive?.content.orEmpty()
                val cover = data["illust"]?.let {
                    runCatching { AppJson.decodeFromJsonElement<Work>(it) }.getOrNull()
                }
                if (name.isBlank() || cover == null || !filter.allows(cover)) null
                else TrendingTag(Tag(name, data["translated_name"]?.jsonPrimitive?.contentOrNull), cover)
            } ?: emptyList()
    }

    suspend fun ugoira(account: Long, id: Long): Ugoira {
        val key = "ugoira-metadata:$id"
        return try {
            val result =
                AppJson.decodeFromJsonElement<UgoiraResponse>(
                        api.get(account, "v1/ugoira/metadata", mapOf("illust_id" to id.toString()))
                    )
                    .ugoira_metadata
            require(result.frames.isNotEmpty() && result.frames.size <= 10000) { "动图帧信息无效" }
            dao.cache(
                CachedFeed(account, key, AppJson.encodeToString(result), System.currentTimeMillis())
            )
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is PixivException) throw e
            dao.cached(account, key)?.let { AppJson.decodeFromString<Ugoira>(it.json) } ?: throw e
        }
    }

    suspend fun novel(account: Long, id: Long): NovelBody {
        val key = "novel-body:$id"
        return try {
            val html = api.text(account, "webview/v2/novel", mapOf("id" to id.toString()))
            val body = parseNovelHtml(html)
            dao.cache(
                CachedFeed(account, key, AppJson.encodeToString(body), System.currentTimeMillis())
            )
            body
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is PixivException) throw e
            dao.cached(account, key)?.let { AppJson.decodeFromString<NovelBody>(it.json) }
                ?: throw e
        }
    }
}

internal fun commentEndpoint(work: Work, parentId: Long?): Pair<String, Map<String, String>> {
    val kind = if (work.isNovel) "novel" else "illust"
    return if (parentId != null)
        "v2/$kind/comment/replies" to mapOf("comment_id" to parentId.toString())
    else
        "v3/$kind/comments" to
            mapOf("${kind}_id" to work.id.toString(), "include_total_comments" to "true")
}
