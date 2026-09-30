package io.github.pixivnext.core

import kotlinx.serialization.Serializable

@Serializable
data class ImageUrls(
    val square_medium: String = "",
    val medium: String = "",
    val large: String = "",
    val original: String = "",
)

@Serializable
data class User(
    val id: Long = 0,
    val name: String = "",
    val account: String = "",
    val profile_image_urls: ImageUrls = ImageUrls(),
    val is_followed: Boolean = false,
    val comment: String = "",
)

@Serializable data class Tag(val name: String = "", val translated_name: String? = null)

data class TrendingTag(val tag: Tag, val cover: Work)

@Serializable data class MetaPage(val image_urls: ImageUrls = ImageUrls())

@Serializable data class SinglePage(val original_image_url: String = "")

@Serializable data class Series(val id: Long = 0, val title: String = "")

@Serializable
data class Work(
    val id: Long = 0,
    val title: String = "",
    val type: String = "illust",
    val image_urls: ImageUrls = ImageUrls(),
    val caption: String = "",
    val user: User = User(),
    val tags: List<Tag> = emptyList(),
    val width: Int = 1,
    val height: Int = 1,
    val page_count: Int = 1,
    val total_view: Int = 0,
    val total_bookmarks: Int = 0,
    val is_bookmarked: Boolean = false,
    val x_restrict: Int = 0,
    val illust_ai_type: Int = 0,
    val create_date: String = "",
    val meta_pages: List<MetaPage> = emptyList(),
    val meta_single_page: SinglePage = SinglePage(),
    val text_length: Int = 0,
    val series: Series? = null,
    val demo: Int = -1,
) {
    val isNovel
        get() = type == "novel"

    val cover
        get() = image_urls.large.ifEmpty { image_urls.medium }

    val aspect
        get() =
            if (width > 1 && height > 1) (width.toFloat() / height).coerceIn(.55f, 1.6f) else .78f

    val originals
        get() =
            if (meta_pages.isNotEmpty())
                meta_pages.map { it.image_urls.original.ifEmpty { it.image_urls.large } }
            else
                listOf(
                    meta_single_page.original_image_url.ifEmpty {
                        image_urls.original.ifEmpty { cover }
                    }
                )
}

@Serializable
data class FeedResponse(
    val illusts: List<Work> = emptyList(),
    val novels: List<Work> = emptyList(),
    val next_url: String? = null,
)

@Serializable data class UserPreview(val user: User = User(), val illusts: List<Work> = emptyList())

@Serializable
data class UserResponse(
    val user_previews: List<UserPreview> = emptyList(),
    val next_url: String? = null,
)

@Serializable data class CommentStamp(val stamp_id: Long = 0, val stamp_url: String = "")

@Serializable
data class Comment(
    val id: Long = 0,
    val comment: String = "",
    val date: String = "",
    val user: User = User(),
    val has_replies: Boolean = false,
    val stamp: CommentStamp? = null,
    val parent_comment: Comment? = null,
)

@Serializable
data class CommentsResponse(
    val comments: List<Comment> = emptyList(),
    val next_url: String? = null,
    val total_comments: Int? = null,
)

data class WorkIdentity(val account: Long, val type: String, val id: Long)

fun Work.identity(account: Long) = WorkIdentity(account, if (isNovel) "novel" else "illust", id)

data class BookmarkState(val selected: Boolean, val count: Int) {
    fun apply(work: Work) = work.copy(is_bookmarked = selected, total_bookmarks = count)
}

data class ContentFilter(
    val adult: Boolean,
    val hideAi: Boolean,
    val blockedTags: String,
    val blockedUsers: String,
)

fun Settings.contentFilter() = ContentFilter(showAdult, hideAi, blockedTags, blockedUsers)

fun ContentFilter.allows(work: Work) =
    Settings(
            showAdult = adult,
            hideAi = hideAi,
            blockedTags = blockedTags,
            blockedUsers = blockedUsers,
        )
        .allows(work)

data class FeedSession(
    val account: Long,
    val demo: Boolean,
    val spec: FeedSpec,
    val filter: ContentFilter,
)

@Serializable
data class Account(
    val user: User,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val premium: Boolean = false,
)

@Serializable data class AuthSession(val verifier: String, val createdAt: Long)

@Serializable
data class VaultData(
    val accounts: List<Account> = emptyList(),
    val activeId: Long? = null,
    val pending: AuthSession? = null,
)

@Serializable data class Frame(val file: String, val delay: Int)

@Serializable data class ZipUrls(val medium: String = "", val original: String = "")

@Serializable
data class Ugoira(val zip_urls: ZipUrls = ZipUrls(), val frames: List<Frame> = emptyList())

@Serializable data class UgoiraResponse(val ugoira_metadata: Ugoira)

@Serializable
data class NovelBody(val text: String = "", val images: Map<String, String> = emptyMap())

data class FeedSpec(
    val section: String = "recommended",
    val kind: String = "illust",
    val word: String = "",
    val mode: String = "day",
    val date: String = "",
    val restrict: String = "public",
    val userId: Long = 0,
    val sort: String = "date_desc",
    val target: String = "partial_match_for_tags",
    val startDate: String = "",
    val endDate: String = "",
)

@Serializable
data class Settings(
    val theme: String = "system",
    val contentKind: String = "illust",
    val dynamicColor: Boolean = true,
    val seed: Long = 0xFF6256CA,
    val blackReader: Boolean = true,
    val showHomeMetadata: Boolean = false,
    val showAdult: Boolean = false,
    val hideAi: Boolean = false,
    val blockedTags: String = "",
    val blockedUsers: String = "",
    val proxyType: String = "system",
    val proxyHost: String = "",
    val proxyPort: Int = 7890,
    val downloadTree: String = "",
    val novelFont: Int = 20,
    val novelSpacing: Int = 32,
)

fun Settings.allows(work: Work): Boolean =
    (showAdult || work.x_restrict == 0) &&
        (!hideAi || work.illust_ai_type != 2) &&
        blockedTags
            .split(',', '\n')
            .filter { it.isNotBlank() }
            .none { blocked -> work.tags.any { it.name.equals(blocked.trim(), true) } } &&
        blockedUsers.split(',', '\n').none { it.trim() == work.user.id.toString() }
