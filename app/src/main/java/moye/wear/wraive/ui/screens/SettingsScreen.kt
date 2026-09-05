package moye.wear.wraive.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.WearSectionHeader

enum class SettingsSection(val title: String) {
    MODELS("模型与助手"),
    TOOLS("对话工具"),
    VOICE("语音"),
    DATA("数据与连接")
}

@Composable
fun SettingsScreen(
    onProviders: () -> Unit,
    onAssistants: () -> Unit,
    onSearch: () -> Unit,
    onMcp: () -> Unit,
    onMemory: () -> Unit,
    onWorldBook: () -> Unit,
    onQuickPhrases: () -> Unit,
    onPromptTransforms: () -> Unit,
    onTranslate: () -> Unit,
    onVoice: () -> Unit,
    onTranscription: () -> Unit,
    onGlobalSearch: () -> Unit,
    onStats: () -> Unit,
    onNetworkProxy: () -> Unit,
    onBackup: () -> Unit,
    onCloudBackup: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    onSection: (SettingsSection) -> Unit,
    section: SettingsSection? = null
) {
    WearListScreen(section?.title ?: "设置") {
        if (section == null) {
            item(key = "appearance") {
                WearActionButton("外观与行为", onAppearance, icon = Icons.Default.ColorLens)
            }
            SettingsSection.entries.forEach { group ->
                item(key = group.name) {
                    WearActionButton(group.title, { onSection(group) },
                        icon = when (group) {
                            SettingsSection.MODELS -> Icons.Default.AutoAwesome
                            SettingsSection.TOOLS -> Icons.Default.Tune
                            SettingsSection.VOICE -> Icons.Default.GraphicEq
                            SettingsSection.DATA -> Icons.Default.Backup
                        })
                }
            }
            item(key = "about") {
                WearActionButton("关于 Wraive", onAbout, icon = Icons.Default.Info)
            }
        }
        if (section == SettingsSection.MODELS) {
            item {
                WearActionButton("模型供应商", onProviders, icon = Icons.Default.Cloud)
            }
            item {
                WearActionButton("助手", onAssistants, icon = Icons.Default.AutoAwesome)
            }
        }
        if (section == SettingsSection.TOOLS) {
            item { WearSectionHeader("检索与工具") }
            item {
                WearActionButton("联网搜索", onSearch, icon = Icons.Default.Search)
            }
            item {
                WearActionButton("MCP 工具", onMcp, icon = Icons.Default.Construction)
            }
            item { WearSectionHeader("记忆与提示词") }
            item {
                WearActionButton("记忆", onMemory, icon = Icons.Default.Memory)
            }
            item {
                WearActionButton("世界书", onWorldBook, icon = Icons.Default.Translate)
            }
            item {
                WearActionButton("快捷短语", onQuickPhrases, icon = Icons.Default.FormatQuote)
            }
            item {
                WearActionButton("提示词转换", onPromptTransforms, icon = Icons.Default.Translate)
            }
            item {
                WearActionButton("翻译工作台", onTranslate, icon = Icons.Default.Translate)
            }
        }
        if (section == SettingsSection.VOICE) {
            item {
                WearActionButton("语音朗读", onVoice, icon = Icons.Default.GraphicEq)
            }
            item {
                WearActionButton("语音输入", onTranscription, icon = Icons.Default.GraphicEq)
            }
        }
        if (section == SettingsSection.DATA) {
            item { WearSectionHeader("记录与用量") }
            item {
                WearActionButton("全局搜索", onGlobalSearch, icon = Icons.Default.Search)
            }
            item {
                WearActionButton("用量与日志", onStats, icon = Icons.Default.BarChart)
            }
            item { WearSectionHeader("连接与备份") }
            item {
                WearActionButton("网络代理", onNetworkProxy, icon = Icons.Default.SettingsEthernet)
            }
            item {
                WearActionButton("备份与恢复", onBackup, icon = Icons.Default.Backup)
            }
            item {
                WearActionButton("S3 云备份", onCloudBackup, icon = Icons.Default.Cloud)
            }
        }
    }
}
