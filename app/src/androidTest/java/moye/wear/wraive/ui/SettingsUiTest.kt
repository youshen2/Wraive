package moye.wear.wraive.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import moye.wear.wraive.model.AppPreferences
import moye.wear.wraive.ui.components.ConfirmActionDialog
import moye.wear.wraive.ui.screens.AboutScreen
import moye.wear.wraive.ui.screens.AppearanceScreen
import moye.wear.wraive.ui.screens.SettingsScreen
import moye.wear.wraive.ui.screens.SettingsSection
import moye.wear.wraive.ui.theme.WraiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun settingsNavigationSelectionAndConfirmationWorkOnRoundScreen() {
        val page = mutableStateOf("settings")
        val section = mutableStateOf<SettingsSection?>(null)
        val preferences = mutableStateOf(AppPreferences(localeTag = "zh", dynamicColor = false))
        val confirming = mutableStateOf(false)
        var confirmed = 0
        compose.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh") {
                AppScaffold {
                    when (page.value) {
                        "about" -> AboutScreen()
                        "appearance" -> AppearanceScreen(preferences.value) { update ->
                            preferences.value = update(preferences.value)
                        }
                        else -> SettingsScreen(
                            onProviders = {}, onAssistants = {}, onSearch = {}, onMcp = {},
                            onMemory = {}, onWorldBook = {}, onQuickPhrases = {},
                            onPromptTransforms = {}, onTranslate = {}, onVoice = {},
                            onTranscription = {}, onGlobalSearch = {}, onStats = {},
                            onNetworkProxy = {}, onBackup = {}, onCloudBackup = {},
                            onAppearance = { page.value = "appearance" },
                            onAbout = { page.value = "about" },
                            onSection = { section.value = it }, section = section.value)
                    }
                    ConfirmActionDialog(confirming.value, "删除会话？",
                        "删除后，会话及其中的消息将无法恢复。", "确认删除",
                        onDismiss = { confirming.value = false },
                        onConfirm = { confirmed++; confirming.value = false })
                }
            }
        }
        capture("settings")
        scrollToText("关于 Wraive")
        compose.onNodeWithText("关于 Wraive").performClick()
        capture("about")
        scrollToText("爅峫")
        compose.onNodeWithText("爅峫").assertIsDisplayed()
        capture("about-author")

        compose.runOnIdle { page.value = "appearance" }
        capture("appearance")
        compose.onNodeWithText("语言").performScrollTo().performClick()
        capture("language-picker")
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("en", preferences.value.localeTag) }

        compose.runOnIdle { confirming.value = true }
        capture("confirmation")
        compose.onNodeWithContentDescription("取消").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, confirmed); confirming.value = true }
        compose.onNodeWithContentDescription("确认删除").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    private fun scrollToText(text: String) {
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(text))
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-qa")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
