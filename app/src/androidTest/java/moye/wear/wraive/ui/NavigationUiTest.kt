package moye.wear.wraive.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import kotlinx.coroutines.runBlocking
import moye.wear.wraive.WraiveApplication
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.ProviderProtocol
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.ui.navigation.AppNavigation
import moye.wear.wraive.ui.theme.WraiveTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class NavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun homeMenuAndNestedSettingsNavigateWithEdgeButtons() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WraiveApplication
        val graph = application.graph
        val originalPreferences = graph.preferences.preferences.value
        val conversations = List(6) { index ->
            Conversation(assistantId = "navigation-preview", title = "会话 ${index + 1}",
                lastMessagePreview = "继续上次的对话", pinned = index == 0,
                updatedAt = System.currentTimeMillis() - index * 1000)
        }
        val archived = Conversation(assistantId = "navigation-preview", title = "归档示例", archived = true)
        val providerA = ProviderConfig(name = "界面预览 A", protocol = ProviderProtocol.OPENAI_CHAT,
            baseUrl = "https://example.invalid/v1")
        val providerB = providerA.copy(id = java.util.UUID.randomUUID().toString(), name = "界面预览 B")
        val modelA = ModelConfig(id = "preview-model", providerId = providerA.id, displayName = "预览模型 A")
        val modelB = modelA.copy(providerId = providerB.id, displayName = "预览模型 B")
        runBlocking {
            (conversations + archived).forEach { graph.repository.upsertConversation(it) }
            listOf(providerA, providerB).forEach { graph.repository.upsertProvider(it) }
            listOf(modelA, modelB).forEach { graph.repository.upsertModel(it) }
        }
        graph.preferences.update { it.copy(localeTag = "zh", dynamicColor = false) }
        try {
            compose.setContent {
                WraiveTheme(dynamicColor = false, localeTag = "zh") {
                    AppScaffold { AppNavigation(graph) }
                }
            }
            compose.onNodeWithText("新对话").assertIsDisplayed()
            capture("home")
            scrollToText("会话 1")
            compose.onNodeWithText("会话 1").assertIsDisplayed()
            scrollToText("会话 6")
            compose.onNodeWithText("会话 6").assertIsDisplayed()
            scrollToEdge("打开菜单")
            capture("home-menu-edge")
            compose.onNodeWithContentDescription("打开菜单").performClick()
            compose.onNodeWithText("新对话").assertIsDisplayed()
            capture("menu")
            scrollToText("归档会话")
            compose.onNodeWithText("归档会话").performClick()
            compose.onNodeWithText("归档示例").assertIsDisplayed()
            compose.onNodeWithText("会话 1").assertDoesNotExist()
            goBack()
            scrollToText("设置")
            compose.onNodeWithText("设置").performClick()
            capture("settings")
            scrollToText("模型与助手")
            compose.onNodeWithText("模型与助手").performClick()
            scrollToEdge("返回")
            capture("settings-section-back")
            scrollToText("模型供应商")
            compose.onNodeWithText("模型供应商").performClick()
            scrollToEdge("返回")
            compose.onNodeWithText(modelA.displayName).assertDoesNotExist()
            compose.onNodeWithText("添加模型").assertDoesNotExist()
            capture("providers-back")
            scrollToText(providerA.name)
            compose.onNodeWithText(providerA.name).performClick()
            capture("provider-details")
            scrollToText(modelA.displayName)
            positionTextNearTop(modelA.displayName)
            compose.onNodeWithText(modelB.displayName).assertDoesNotExist()
            capture("provider-models")
            compose.onNodeWithText(modelA.displayName).performClick()
            compose.onNodeWithText("显示名称").performTextReplacement("预览模型 A 已编辑")
            scrollToText("取消")
            compose.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
            compose.onNodeWithText("保存").performClick()
            scrollToText("预览模型 A 已编辑")
            compose.onNodeWithText("预览模型 A 已编辑").assertIsDisplayed()
            goBack()
            scrollToText(providerB.name)
            compose.onNodeWithText(providerB.name).performClick()
            scrollToText(modelB.displayName)
            compose.onNodeWithText(modelB.displayName).assertIsDisplayed()
            compose.onNodeWithText("预览模型 A 已编辑").assertDoesNotExist()
            goBack()
            goBack()
            compose.onNodeWithText("助手").assertIsDisplayed()
            goBack()
            scrollToText("关于 Wraive")
            compose.onNodeWithText("关于 Wraive").performClick()
            capture("about")
            scrollToText("开源地址")
            positionTextNearTop("开源地址")
            capture("about-source")
            compose.onNodeWithText("开源地址").performClick()
            compose.onNodeWithContentDescription("开源仓库二维码").assertIsDisplayed()
            capture("source-qr")
            goBack()
            scrollToText("爅峫")
            compose.onNodeWithContentDescription("开发者头像").assertIsDisplayed()
            capture("about-author")
            scrollToText("Kelivo")
            positionTextNearTop("Kelivo")
            capture("about-kelivo")
            scrollToText("更新内容")
            positionTextNearTop("更新内容")
            capture("about-changelog")
            goBack()
            compose.onNodeWithText("关于 Wraive").assertIsDisplayed()
            goBack()
            goBack()
            compose.onNodeWithContentDescription("打开菜单").assertIsDisplayed()
        } finally {
            runBlocking {
                (conversations + archived).forEach { graph.repository.deleteConversation(it.id) }
                listOf(providerA, providerB).forEach { graph.repository.deleteProvider(it.id) }
            }
            graph.preferences.replace(originalPreferences)
        }
    }

    private fun scrollToText(text: String) {
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(text))
    }

    private fun scrollToEdge(description: String) {
        repeat(10) {
            val reachedEdge = compose.onAllNodes(hasContentDescription(description)).onLast().isDisplayed()
            compose.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
            compose.waitForIdle()
            if (reachedEdge) return
        }
        compose.onAllNodes(hasContentDescription(description)).onLast().assertIsDisplayed()
    }

    private fun positionTextNearTop(text: String) {
        val offset = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y - 100f
        compose.onAllNodes(hasScrollAction()).onLast().performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, offset)
        }
    }

    private fun goBack() {
        scrollToEdge("返回")
        compose.onAllNodes(hasContentDescription("返回")).onLast().performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "navigation-qa")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
