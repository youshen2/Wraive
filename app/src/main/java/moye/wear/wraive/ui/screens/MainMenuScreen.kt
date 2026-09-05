package moye.wear.wraive.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import moye.wear.wraive.model.AssistantProfile
import moye.wear.wraive.model.ModelConfig
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.WearSelectionDialog

@Composable
fun MainMenuScreen(
    assistants: List<AssistantProfile>,
    providers: List<ProviderConfig>,
    models: List<ModelConfig>,
    onNewConversation: (AssistantProfile, Boolean) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val readyProviderIds = providers
        .filter { it.enabled && it.baseUrl.isNotBlank() }
        .mapTo(mutableSetOf(), ProviderConfig::id)
    val readyModels = models.filter { it.enabled && it.providerId in readyProviderIds }
    val readyAssistants = assistants.filter { assistant ->
        assistant.model?.let { selected ->
            readyModels.any { it.providerId == selected.providerId && it.id == selected.modelId }
        } == true
    }
    var choosingAssistant by remember { mutableStateOf(false) }
    var temporary by remember { mutableStateOf(false) }

    fun startConversation(isTemporary: Boolean) {
        when {
            readyModels.isEmpty() -> onOpenProviders()
            readyAssistants.isEmpty() -> onOpenAssistants()
            readyAssistants.size == 1 -> onNewConversation(readyAssistants.first(), isTemporary)
            else -> {
                temporary = isTemporary
                choosingAssistant = true
            }
        }
    }

    WearListScreen("菜单") {
        item(key = "new-conversation") {
            WearActionButton("新对话", { startConversation(false) },
                icon = Icons.Default.Add, primary = true,
                secondary = when {
                    readyModels.isEmpty() -> "先连接模型服务"
                    readyAssistants.isEmpty() -> "先为助手选择模型"
                    else -> null
                })
        }
        item(key = "temporary-conversation") {
            WearActionButton("临时对话", { startConversation(true) },
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
    readyAssistants.firstOrNull()?.let { firstAssistant ->
        WearSelectionDialog(
            visible = choosingAssistant,
            title = "选择助手",
            selected = firstAssistant,
            options = readyAssistants,
            onDismiss = { choosingAssistant = false },
            onSelect = { onNewConversation(it, temporary) },
            optionLabel = AssistantProfile::name
        )
    }
}
