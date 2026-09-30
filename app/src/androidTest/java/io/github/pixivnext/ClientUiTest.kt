package io.github.pixivnext

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun preview() {
        ui.activityRule.scenario.onActivity {
            val vm = androidx.lifecycle.ViewModelProvider(it)[AppViewModel::class.java]
            kotlinx.coroutines.runBlocking {
                vm.settingsStore.update { settings -> settings.copy(showHomeMetadata = true) }
            }
        }
        ui.onNodeWithText("先体验界面").performScrollTo().performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("海风经过的午后").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun opensArtworkBookmarksAndReadsOriginal() {
        preview()
        ui.onNodeWithText("海风经过的午后").performClick()
        ui.onNodeWithText("收藏作品").performClick()
        ui.onNodeWithText("已收藏").assertExists()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("作品详情").assertExists()
        ui.onNodeWithText("查看原图").assertDoesNotExist()
        ui.onNodeWithTag("detailList").performScrollToNode(hasTestTag("detailImage"))
        ui.onNodeWithTag("detailImage").performClick()
        ui.onNodeWithContentDescription("返回").assertExists()
        ui.activityRule.scenario.onActivity {
            org.junit.Assert.assertEquals(
                0,
                it.window.insetsController!!.systemBarsAppearance and
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        }
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("作品详情").assertExists()
        ui.onNodeWithContentDescription("更多操作").performClick()
        ui.onNodeWithText("查看原图").assertExists().performClick()
        ui.onNodeWithContentDescription("返回").assertExists()
    }

    @Test
    fun searchesDemoAndOpensNovelReader() {
        preview()
        ui.onAllNodesWithText("搜索", useUnmergedTree = true).assertCountEquals(1)
        ui.onNodeWithContentDescription("搜索").assertDoesNotExist()
        ui.onNodeWithText("搜索", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("返回").assertDoesNotExist()
        ui.onAllNodesWithText("搜索", useUnmergedTree = true).assertCountEquals(1)
        ui.onNodeWithText("作品、标签或创作者").performTextInput("海风")
        ui.onNodeWithText("海风").performImeAction()
        ui.onNodeWithText("海风经过的午后").assertExists()
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("首页", useUnmergedTree = true).performClick()
        ui.onNodeWithText("小说").performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("直到下一场雨").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("直到下一场雨").performClick()
        ui.onNodeWithTag("detailList").performScrollToNode(hasText("开始阅读"))
        ui.onNodeWithText("开始阅读").performClick()
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("第一章  风从海边来", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithContentDescription("阅读设置").performClick()
        ui.onNodeWithText("阅读设置").assertExists()
    }

    @Test
    fun configuresThemeAndProxyThenRestoresDefaults() {
        preview()
        ui.onNodeWithText("我的", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("主题").performClick()
        ui.onNode(hasText("深色") and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithTag("settingsList").performScrollToNode(hasText("连接方式"))
        ui.onNodeWithText("连接方式").performClick()
        ui.onNodeWithText("HTTP 代理").performClick()
        ui.onNodeWithText("代理主机").performTextInput("127.0.0.1")
        ui.onNodeWithText("保存").performClick()
        ui.onNodeWithText("连接方式").performClick()
        ui.onNode(hasText("系统网络") and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithText("保存").performClick()
        ui.onNodeWithTag("settingsList").performScrollToNode(hasText("主题"))
        ui.onNodeWithText("主题").performClick()
        ui.onNode(hasText("跟随系统") and hasAnyAncestor(isDialog())).performClick()
    }

    @Test
    fun userInitiatedDownloadPublishesAnAppOwnedMediaStoreFile() {
        preview()
        lateinit var model: AppViewModel
        ui.activityRule.scenario.onActivity {
            model = androidx.lifecycle.ViewModelProvider(it)[AppViewModel::class.java]
        }
        val id =
            kotlinx.coroutines.runBlocking {
                model.dao.enqueue(
                    io.github.pixivnext.core.DownloadEntity(
                        accountId = -1,
                        workId = -999,
                        kind = "frames",
                        page = 0,
                        title = "UIDT integration fixture",
                        url = "frames:{\"fixture\":true}",
                        name = "pixivnext_qa_uidt.json",
                    )
                )
            }
        var output: android.net.Uri? = null
        try {
            ui.activityRule.scenario.onActivity { model.downloads.schedule() }
            ui.waitUntil(20000) {
                kotlinx.coroutines.runBlocking {
                    model.dao.download(id)?.status in listOf("complete", "failed")
                }
            }
            val record = kotlinx.coroutines.runBlocking { model.dao.download(id)!! }
            org.junit.Assert.assertEquals(record.error, "complete", record.status)
            output = android.net.Uri.parse(record.uri)
            val context =
                androidx.test.core.app.ApplicationProvider.getApplicationContext<
                    android.content.Context
                >()
            org.junit.Assert.assertEquals(
                "{\"fixture\":true}",
                context.contentResolver.openInputStream(output!!)!!.bufferedReader().use {
                    it.readText()
                },
            )
        } finally {
            output?.let {
                androidx.test.core.app.ApplicationProvider.getApplicationContext<
                        android.content.Context
                    >()
                    .contentResolver
                    .delete(it, null, null)
            }
            kotlinx.coroutines.runBlocking { model.dao.deleteDownload(id) }
        }
    }
}
