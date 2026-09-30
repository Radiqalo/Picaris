package io.github.pixivnext

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pixivnext.core.*
import org.junit.*
import org.junit.runner.RunWith

@Ignore("Offline demo removed; requires an isolated API fixture")
@RunWith(AndroidJUnit4::class)
class NavigationRegressionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun requireIsolatedContentFixture() {
        error("Content scenarios require an isolated API fixture")
    }

    @Test
    fun likesFromCardAndKeepsFeedAcrossDetailsAndTabs() {
        requireIsolatedContentFixture()
        lateinit var vm: AppViewModel
        ui.activityRule.scenario.onActivity {
            vm = androidx.lifecycle.ViewModelProvider(it)[AppViewModel::class.java]
        }
        val session = vm.feed(FeedSpec())
        ui.onNodeWithTag("like_illust_1").performClick()
        ui.onNodeWithTag("like_illust_1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已喜欢"))
        ui.onNodeWithText("作品详情").assertDoesNotExist()
        ui.onNodeWithText("海风经过的午后").performClick()
        ui.onNodeWithText("已收藏").assertExists().performClick()
        ui.onNodeWithText("收藏作品").assertExists()
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithTag("like_illust_1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未喜欢"))
        ui.onNodeWithTag("feedGrid").performScrollToIndex(4)
        val position =
            ui.onNodeWithTag("feedGrid")
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
                .value()
        ui.onNodeWithText("森林收集者").performClick()
        ui.onNodeWithText("作品详情").assertExists()
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("我的", useUnmergedTree = true).performClick()
        ui.onNodeWithText("发现", useUnmergedTree = true).performClick()
        Assert.assertSame(session, vm.feed(FeedSpec()))
        Assert.assertEquals(
            position,
            ui.onNodeWithTag("feedGrid")
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
                .value(),
            .001f,
        )
    }

    @Test
    fun repliesToCommentWithoutRepliesOpenComposerDirectly() {
        requireIsolatedContentFixture()
        ui.onNodeWithText("海风经过的午后").performClick()
        ui.onNodeWithContentDescription("评论区").performClick()
        ui.onNodeWithText("回复", substring = false).performScrollTo().performClick()
        ui.onNodeWithTag("commentInput").assertExists().performTextInput("第一条本地回复")
        ui.onNodeWithText("评论回复").assertDoesNotExist()
        ui.onNodeWithText("发送").performClick()
        ui.onNodeWithTag("commentInput").assertDoesNotExist()
        Assert.assertEquals(2, ui.onAllNodesWithText("查看回复").fetchSemanticsNodes().size)
    }

    @Test
    fun showsFollowingAndSupportsLocalCommentsAndReplies() {
        requireIsolatedContentFixture()
        ui.onNodeWithText("我的", useUnmergedTree = true).performClick()
        ui.onNodeWithTag("profileList").performScrollToNode(hasText("我的关注"))
        ui.onNodeWithText("我的关注").performClick()
        ui.onNodeWithText("公开关注").assertExists()
        ui.onNodeWithText("非公开关注").performClick()
        ui.onNodeWithTag("peopleList").assertExists()
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("发现", useUnmergedTree = true).performClick()
        ui.onNodeWithText("海风经过的午后").performClick()
        ui.onNodeWithContentDescription("评论区").performClick()
        ui.onNodeWithText("很喜欢这幅作品的氛围。").assertExists()
        ui.onNodeWithText("查看回复").performClick()
        ui.onNodeWithText("谢谢喜欢！").assertExists()
        ui.onNodeWithText("回复 mori").performClick()
        ui.onNodeWithTag("commentInput").performTextInput("仅本地的回复测试")
        ui.onNodeWithText("发送").performClick()
        ui.onNodeWithText("仅本地的回复测试").assertExists()
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("写评论").performClick()
        ui.onNodeWithTag("commentInput").performTextInput("仅本地的评论测试")
        ui.onNodeWithText("发送").performClick()
        ui.onNodeWithText("仅本地的评论测试").assertExists()
    }
}
