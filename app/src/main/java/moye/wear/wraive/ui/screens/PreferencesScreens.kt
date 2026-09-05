package moye.wear.wraive.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import moye.wear.wraive.data.BackupService
import moye.wear.wraive.data.ForeignImportService
import moye.wear.wraive.model.AppPreferences
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.WearSectionHeader
import moye.wear.wraive.ui.components.WearSelectionField
import moye.wear.wraive.ui.tr

@Composable
fun AppearanceScreen(
    preferences: AppPreferences,
    onUpdate: ((AppPreferences) -> AppPreferences) -> Unit
) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdate { it.copy(backgroundGeneration = granted) }
    }
    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            val file = File(context.filesDir, "custom-font.ttf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use(input::copyTo)
            } ?: error("无法读取字体文件")
            onUpdate { it.copy(customFontPath = file.absolutePath) }
        }
    }
    WearListScreen("外观与行为") {
        item { WearSectionHeader("显示") }
        item {
            PreferenceSwitch(
                "动态配色",
                "跟随手表系统主题",
                preferences.dynamicColor
            ) { checked -> onUpdate { it.copy(dynamicColor = checked) } }
        }
        item {
            WearSelectionField("语言", preferences.localeTag, listOf("system", "zh", "en"),
                onSelect = { value -> onUpdate { it.copy(localeTag = value) } },
                optionLabel = { value -> when (value) {
                    "zh" -> "简体中文"
                    "en" -> "English"
                    else -> "跟随系统"
                } })
        }
        item {
            WearActionButton(
                label = if (preferences.customFontPath.isBlank()) "选择自定义字体" else "更换自定义字体",
                secondary = "支持 TTF / OTF",
                icon = Icons.Default.FontDownload,
                onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) }
            )
        }
        if (preferences.customFontPath.isNotBlank()) {
            item {
                WearActionButton(
                    "恢复系统字体",
                    {
                        File(preferences.customFontPath).delete()
                        onUpdate { it.copy(customFontPath = "") }
                    },
                    icon = Icons.Default.Delete
                )
            }
        }
        item { WearSectionHeader("回复内容") }
        item {
            PreferenceSwitch(
                "显示思考过程",
                "可在回复气泡中展开",
                preferences.showReasoning
            ) { checked -> onUpdate { it.copy(showReasoning = checked) } }
        }
        item {
            PreferenceSwitch(
                "显示供应商",
                "在消息详情展示来源",
                preferences.showProvider
            ) { checked -> onUpdate { it.copy(showProvider = checked) } }
        }
        item {
            PreferenceSwitch(
                "Markdown 渲染",
                "代码、强调与列表格式",
                preferences.markdownEnabled
            ) { checked -> onUpdate { it.copy(markdownEnabled = checked) } }
        }
        item { WearSectionHeader("对话行为") }
        item {
            PreferenceSwitch(
                "后台生成",
                "离开应用后继续接收回复",
                preferences.backgroundGeneration
            ) { checked ->
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onUpdate { it.copy(backgroundGeneration = checked) }
                }
            }
        }
        item {
            PreferenceSwitch(
                "上下文压缩",
                "接近模型限制时自动总结早期消息",
                preferences.contextCompressionEnabled
            ) { checked -> onUpdate { it.copy(contextCompressionEnabled = checked) } }
        }
        item {
            PreferenceSwitch(
                "自动标题",
                "使用首条消息生成会话标题",
                preferences.autoTitle
            ) { checked -> onUpdate { it.copy(autoTitle = checked) } }
        }
        item {
            PreferenceSwitch(
                "自动朗读回复",
                "使用当前语音服务朗读新回复",
                preferences.autoSpeakReplies
            ) { checked -> onUpdate { it.copy(autoSpeakReplies = checked) } }
        }
        item { WearSectionHeader("操作反馈") }
        item {
            PreferenceSwitch(
                "触觉反馈",
                "旋钮与操作反馈",
                preferences.hapticsEnabled
            ) { checked -> onUpdate { it.copy(hapticsEnabled = checked) } }
        }
    }
}

@Composable
private fun PreferenceSwitch(
    label: String,
    secondary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(tr(label), style = MaterialTheme.typography.labelMedium) },
        secondaryLabel = { Text(tr(secondary), style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun BackupScreen(
    backupService: BackupService,
    foreignImportService: ForeignImportService
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { backupService.export(it) }
                    ?: error("无法创建备份文件")
            }.onSuccess {
                status = "备份已导出"
            }.onFailure {
                status = it.message
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { backupService.import(it) }
                    ?: error("无法读取备份文件")
            }.onSuccess {
                status = "恢复完成"
            }.onFailure {
                status = it.message
            }
        }
    }
    val foreignImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val name = uri.lastPathSegment.orEmpty()
                context.contentResolver.openInputStream(uri)?.use {
                    foreignImportService.import(it, name)
                } ?: error("无法读取导入文件")
            }.onSuccess {
                status = it.displayText()
            }.onFailure {
                status = it.message
            }
        }
    }
    WearListScreen("备份与恢复") {
        item {
            Text(
                tr("备份包含会话、助手、供应商、密钥、搜索、MCP、记忆与设置。请妥善保管文件。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            WearActionButton(
                "导出完整备份",
                { exportLauncher.launch("Wraive-backup.json") },
                icon = Icons.Default.Upload,
                primary = true
            )
        }
        item {
            WearActionButton(
                "从备份恢复",
                { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                icon = Icons.Default.Download
            )
        }
        item {
            WearActionButton(
                "导入应用备份",
                { foreignImportLauncher.launch(arrayOf("application/json", "application/zip", "*/*")) },
                secondary = "Chatbox / Cherry · JSON / ZIP",
                icon = Icons.Default.Download
            )
        }
        status?.let { item { Text(tr(it), color = MaterialTheme.colorScheme.primary) } }
    }
}
