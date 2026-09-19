package moye.wear.wraive.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearListScreen

@Composable
fun MainMenuScreen(
    onNewConversation: (Boolean) -> Unit,
    newConversationHint: String?,
    onOpenArchive: () -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenSettings: () -> Unit
) {
    WearListScreen("菜单") {
        item(key = "new-conversation") {
            WearActionButton("新对话", { onNewConversation(false) },
                icon = Icons.Default.Add, primary = true,
                secondary = newConversationHint)
        }
        item(key = "temporary-conversation") {
            WearActionButton("临时对话", { onNewConversation(true) },
                icon = Icons.Default.VisibilityOff, secondary = "关闭后不写入历史记录")
        }
        item(key = "assistants") {
            WearActionButton("管理助手", onOpenAssistants, icon = Icons.Default.AutoAwesome)
        }
        item(key = "archive") {
            WearActionButton("归档会话", onOpenArchive, icon = Icons.Default.Archive)
        }
        item(key = "settings") {
            WearActionButton("设置", onOpenSettings, icon = Icons.Default.Settings)
        }
    }
}
