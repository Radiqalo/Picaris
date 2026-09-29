package io.github.pixivnext.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.ThreadState
import io.github.pixivnext.core.*
import io.github.pixivnext.designsystem.*

@Composable
fun PeopleScreen(route: People, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    var restrict by rememberSaveable { mutableStateOf("public") }
    val flow =
        remember(route.section, restrict, vm.accountId) { vm.people(route.section, restrict) }
    val people = flow.collectAsLazyPagingItems()
    val list = rememberLazyListState()
    Column {
        ScreenBar(route.title, back = back)
        if (route.section == "following")
            Row(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(restrict == "public", { restrict = "public" }, label = { Text("公开关注") })
                FilterChip(
                    restrict == "private",
                    { restrict = "private" },
                    label = { Text("非公开关注") },
                )
            }
        PullToRefreshBox(
            people.loadState.refresh is LoadState.Loading && people.itemCount > 0,
            { people.refresh() },
            Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                people.loadState.refresh is LoadState.Loading && people.itemCount == 0 ->
                    LoadingState()
                people.loadState.refresh is LoadState.Error && people.itemCount == 0 ->
                    EmptyState(
                        "加载失败",
                        (people.loadState.refresh as LoadState.Error).error.message ?: "请重试",
                        Glyph.Person,
                        "重试",
                        { people.retry() },
                    )
                people.itemCount == 0 ->
                    EmptyState("还没有用户", "这里会显示${route.title}的用户。", Glyph.Person)
                else ->
                    LazyColumn(
                        state = list,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize().testTag("peopleList"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(people.itemCount, key = people.itemKey { it.id }) { index ->
                            people[index]?.let { user ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    UserRow(user, { navigate(Author(user)) }) {
                                        if (user.is_followed)
                                            Text(
                                                "已关注",
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                    }
                                }
                            }
                        }
                        item {
                            when (people.loadState.append) {
                                is LoadState.Loading ->
                                    Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LoadingIndicator()
                                    }
                                is LoadState.Error ->
                                    TextButton({ people.retry() }, Modifier.fillMaxWidth()) {
                                        Text("加载更多失败，重试")
                                    }
                                else -> Unit
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun thread(work: Work, vm: AppViewModel, parentId: Long? = null): ThreadState {
    val account = vm.accountId
    val state by
        remember(account, work.id, work.type, parentId) {
                vm.comments.state(account, work, parentId)
            }
            .collectAsStateWithLifecycle()
    LaunchedEffect(account, work.id, work.type, parentId) {
        if (!state.loaded) vm.comments.load(account, work, parentId)
    }
    return state
}

@Composable
fun CommentPreview(work: Work, vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val state = thread(work, vm)
    var replyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var replyName by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "评论${state.total?.let { " · $it" } ?: ""}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton({ navigate(Comments(work)) }) { Text("查看全部") }
        }
        state.comments.take(3).forEach { comment ->
            CommentCard(comment, work, navigate) {
                if (comment.has_replies) navigate(Replies(work, comment))
                else {
                    replyId = comment.id
                    replyName = comment.user.name
                }
            }
        }
        if (state.comments.isEmpty()) CommentFooter(state) { vm.comments.load(vm.accountId, work) }
        OutlinedButton({ navigate(Comments(work)) }, Modifier.fillMaxWidth()) {
            AppIcon(Glyph.Comment, null)
            Spacer(Modifier.width(8.dp))
            Text("写评论")
        }
    }
    replyId?.let { ReplyComposer(work, it, replyName, vm) { replyId = null } }
}

@Composable
fun CommentsScreen(work: Work, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) =
    ThreadScreen(work, null, vm, navigate, back)

@Composable
fun RepliesScreen(route: Replies, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) =
    ThreadScreen(route.work, route.comment, vm, navigate, back)

@Composable
private fun ThreadScreen(
    work: Work,
    parent: Comment?,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    back: () -> Unit,
) {
    val state = thread(work, vm, parent?.id)
    var composer by rememberSaveable { mutableStateOf(false) }
    var replyName by rememberSaveable { mutableStateOf(parent?.user?.name ?: "") }
    var directReplyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var directReplyName by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = LocalAppBarScrollBehavior.current,
                title = { Text(if (parent == null) "评论区" else "评论回复") },
                navigationIcon = { IconButton(back) { AppIcon(Glyph.Back, "返回") } },
                actions = {
                    TextButton(
                        { vm.comments.load(vm.accountId, work, parent?.id, refresh = true) },
                        enabled = !state.loading && !state.sending,
                    ) {
                        Text("刷新")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                FilledTonalButton(
                    {
                        replyName = parent?.user?.name ?: ""
                        composer = true
                    },
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    enabled = !state.sending,
                ) {
                    AppIcon(Glyph.Comment, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (parent == null) "写评论" else "回复 ${parent.user.name}")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("commentsList"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(work.title, style = MaterialTheme.typography.titleMedium) }
            if (work.demo >= 0)
                item {
                    Text(
                        "本地演示评论，不会发送到 Pixiv。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            if (parent != null) item { CommentCard(parent, work, navigate, onReply = null) }
            items(state.comments, key = { it.id }) { comment ->
                CommentCard(comment, work, navigate) {
                    if (parent == null && comment.has_replies) navigate(Replies(work, comment))
                    else if (parent == null) {
                        directReplyId = comment.id
                        directReplyName = comment.user.name
                    } else {
                        replyName = comment.user.name
                        composer = true
                    }
                }
            }
            item { CommentFooter(state) { vm.comments.load(vm.accountId, work, parent?.id) } }
        }
    }
    if (composer)
        CommentComposer(work, state, replyName, { composer = false }) { text ->
            vm.comments.send(vm.accountId, work, text, parent?.id) { composer = false }
        }
    directReplyId?.let {
        ReplyComposer(work, it, directReplyName, vm) { directReplyId = null }
    }
}

@Composable
private fun ReplyComposer(
    work: Work,
    parentId: Long,
    replyName: String,
    vm: AppViewModel,
    dismiss: () -> Unit,
) {
    val account = vm.accountId
    val state by
        remember(account, work.id, work.type, parentId) {
                vm.comments.state(account, work, parentId)
            }
            .collectAsStateWithLifecycle()
    CommentComposer(work, state, replyName, dismiss) { text ->
        vm.comments.send(account, work, text, parentId, dismiss)
    }
}

@Composable
private fun CommentCard(
    comment: Comment,
    work: Work,
    navigate: (NavKey) -> Unit,
    onReply: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.clickable { navigate(Author(comment.user)) }) {
                    Avatar(comment.user, Modifier.size(36.dp))
                }
                Column(Modifier.weight(1f).clickable { navigate(Author(comment.user)) }) {
                    Text(comment.user.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        comment.date.replace('T', ' ').take(16),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (comment.user.id == work.user.id)
                    Text(
                        "作者",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
            }
            comment.parent_comment?.let {
                Text(
                    "回复 ${it.user.name}：${it.comment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (comment.comment.isNotEmpty())
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(comment.comment, style = MaterialTheme.typography.bodyLarge)
                }
            comment.stamp
                ?.takeIf { it.stamp_url.isNotEmpty() }
                ?.let {
                    AsyncImage(it.stamp_url, "贴图评论", Modifier.size(88.dp))
                }
            if (onReply != null)
                TextButton(onReply, Modifier.align(Alignment.End)) {
                    Text(if (comment.has_replies) "查看回复" else "回复")
                }
        }
    }
}

@Composable
private fun CommentFooter(state: ThreadState, load: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.loading -> LoadingIndicator(Modifier.size(32.dp))
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                TextButton(load) { Text("重新加载评论") }
            }
            state.next != null -> TextButton(load) { Text("加载更多评论") }
            state.comments.isEmpty() ->
                Text("还没有评论，来留下第一句喜欢吧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else ->
                Text(
                    "已经看到这里了",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun CommentComposer(
    work: Work,
    state: ThreadState,
    replyName: String,
    dismiss: () -> Unit,
    send: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val count = text.codePointCount(0, text.length)
    AlertDialog(
        onDismissRequest = { if (!state.sending) dismiss() },
        title = { Text(if (replyName.isEmpty()) "写评论" else "回复 $replyName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (work.demo >= 0) Text("仅保存为本地演示评论。")
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth().testTag("commentInput"),
                    enabled = !state.sending,
                    label = { Text("评论内容") },
                    minLines = 3,
                    maxLines = 6,
                    isError = count > 140,
                    supportingText = { Text("$count / 140") },
                )
                state.sendError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                { send(text) },
                enabled = !state.sending && text.isNotBlank() && count <= 140,
            ) {
                Text(if (state.sending) "发送中…" else "发送")
            }
        },
        dismissButton = { TextButton(dismiss, enabled = !state.sending) { Text("取消") } },
    )
}
