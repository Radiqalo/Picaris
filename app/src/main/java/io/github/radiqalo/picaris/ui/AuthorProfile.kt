package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.core.AuthorDetails
import io.github.radiqalo.picaris.designsystem.PixivSpacing

@Composable
fun AuthorProfileContent(details: AuthorDetails, vm: AppViewModel, loaded: Boolean) {
    val user = details.user
    val profile = details.profile
    val publicity = details.profile_publicity
    val workspace = details.workspace
    val uriHandler = LocalUriHandler.current
    val own = user.id == vm.accountId
    fun visible(value: String) = own || value.isBlank() || value == "public"
    val personal = buildList {
        add("Pixiv ID" to user.id.toString())
        if (user.account.isNotBlank()) add("账号" to user.account)
        if (visible(publicity.gender)) add("性别" to when (profile.gender) {
            "male" -> "男"; "female" -> "女"; else -> profile.gender
        })
        if (visible(publicity.region)) add("地区" to profile.region.orEmpty())
        if (visible(publicity.birth_year) && profile.birth_year > 0)
            add("出生年份" to profile.birth_year.toString())
        if (visible(publicity.birth_day)) add("生日" to profile.birth_day.ifBlank {
            profile.birth.takeLast(5).takeIf { it.matches(Regex("\\d{2}-\\d{2}")) }.orEmpty()
        })
        if (visible(publicity.job)) add("职业" to profile.job)
    }.filter { it.second.isNotBlank() }
    val twitter = profile.twitter_url?.takeIf { it.isNotBlank() }
        ?: profile.twitter_account.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,15}")) }?.let { "https://x.com/$it" }
    val links = listOf("个人网站" to profile.webpage, "X / Twitter" to twitter,
        "Pawoo" to profile.pawoo_url).mapNotNull { (label, value) ->
        value?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { label to it }
    }
    val tools = listOf("电脑" to workspace.pc, "显示器" to workspace.monitor,
        "绘画软件" to workspace.tool, "扫描仪" to workspace.scanner, "数位板" to workspace.tablet,
        "鼠标" to workspace.mouse, "打印机" to workspace.printer, "桌面布置" to workspace.desktop,
        "创作时听的音乐" to workspace.music, "桌子" to workspace.desk, "椅子" to workspace.chair,
        "其他说明" to workspace.comment).filter { it.second.isNotBlank() }
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.85f),
        contentPadding = PaddingValues(PixivSpacing.content),
        verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
        item { Text(user.name, style = MaterialTheme.typography.headlineSmall) }
        if (!loaded) item { Text("资料暂未加载成功", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (user.comment.isNotBlank()) {
            item { Text("自我介绍", style = MaterialTheme.typography.titleMedium) }
            item { SelectionContainer { Text(user.comment, style = MaterialTheme.typography.bodyLarge) } }
        }
        item { Text("个人资料", style = MaterialTheme.typography.titleMedium) }
        items(personal, key = { it.first }) { (label, value) -> ProfileField(label, value) }
        if (links.isNotEmpty()) {
            item { Text("网站与社交账号", style = MaterialTheme.typography.titleMedium) }
            items(links, key = { it.first }) { (label, value) ->
                TextButton(onClick = { vm.run { uriHandler.openUri(value) } }) { Text(label) }
            }
        }
        if (loaded) {
            item { Text("作品与收藏", style = MaterialTheme.typography.titleMedium) }
            items(listOf("插画" to profile.total_illusts, "漫画" to profile.total_manga,
                "小说" to profile.total_novels, "公开收藏" to profile.total_illust_bookmarks_public,
                "关注" to profile.total_follow_users, "插画系列" to profile.total_illust_series,
                "小说系列" to profile.total_novel_series)) { (label, count) -> ProfileField(label, count.toString()) }
        }
        if (tools.isNotEmpty() || !workspace.workspace_image_url.isNullOrBlank()) {
            item { Text("创作环境", style = MaterialTheme.typography.titleMedium) }
            workspace.workspace_image_url?.takeIf { it.isNotBlank() }?.let { url ->
                item { AsyncImage(url, "作者创作环境", Modifier.fillMaxWidth()) }
            }
            items(tools, key = { it.first }) { (label, value) -> ProfileField(label, value) }
        }
        item { TextButton(onClick = { vm.run { uriHandler.openUri("https://www.pixiv.net/users/${user.id}") } }) {
            Text("在 Pixiv 查看")
        } }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun ProfileField(label: String, value: String) {
    ListItem(headlineContent = { Text(label, style = MaterialTheme.typography.labelLarge) },
        supportingContent = { SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
}
