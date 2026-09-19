package moye.wear.wraive.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.wear.compose.material3.AppScaffold
import moye.wear.wraive.ui.components.Watch5ConfirmDialog
import moye.wear.wraive.ui.theme.WraiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Watch5CompatibilityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun watch5ConfirmationScrollsAndKeepsCancelSeparateFromConfirm() {
        val visible = mutableStateOf(true)
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh") {
                AppScaffold {
                    Watch5ConfirmDialog(
                        visible = visible.value,
                        title = "删除会话？",
                        message = "删除后，会话及其中的消息将无法恢复。\n".repeat(8),
                        confirmLabel = "确认删除",
                        onDismiss = { dismissed++; visible.value = false },
                        onConfirm = { confirmed++; visible.value = false }
                    )
                }
            }
        }
        scrollToActions()
        compose.onNodeWithContentDescription("取消").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("确认删除").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, confirmed)
            assertEquals(1, dismissed)
            visible.value = true
        }
        scrollToActions()
        compose.onNodeWithContentDescription("确认删除").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("取消").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, confirmed)
            assertEquals(1, dismissed)
        }
    }

    private fun scrollToActions() {
        compose.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasContentDescription("确认删除"))
    }
}
