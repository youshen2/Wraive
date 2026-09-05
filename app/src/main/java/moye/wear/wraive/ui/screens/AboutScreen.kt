package moye.wear.wraive.ui.screens

import android.os.Build
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Code
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moye.wear.wraive.BuildConfig
import moye.wear.wraive.R
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import moye.wear.wraive.ui.components.WearListScreen
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearBackButton
import moye.wear.wraive.ui.tr
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private const val SOURCE_URL = "https://github.com/youshen2/Wraive"

@Composable
fun AboutScreen() {
    var showingSource by remember { mutableStateOf(false) }
    WearListScreen("关于软件") {
        item(key = "app-identity") { AppIdentity() }
        item(key = "developer-section") { AboutSectionLabel("开发者") }
        item(key = "developer") { DeveloperPanel() }
        item(key = "source-section") { AboutSectionLabel("开源项目") }
        item(key = "source") {
            WearActionButton("开源地址", { showingSource = true },
                icon = Icons.Default.Code, secondary = SOURCE_URL.removePrefix("https://"))
        }
        item(key = "credits-section") { AboutSectionLabel("参考与致谢") }
        item(key = "kelivo-credit") {
            AboutPanel("Kelivo", Icons.Default.Code) {
                Text(tr("本应用参考了 Kelivo 项目，感谢其带来的启发。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        item(key = "changelog-section") { AboutSectionLabel("版本记录") }
        item(key = "changelog") {
            AboutPanel("更新内容", Icons.Default.History) {
                Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))
                ChangelogBulletItem("首页会话列表与底部菜单")
                ChangelogBulletItem("设置分组、选择器与确认对话框")
                ChangelogBulletItem("优化 AI 流式回复的滚动稳定性")
            }
        }
        item(key = "footer") {
            Text("Wraive · Made for Wear OS",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
    if (showingSource) SourceAddressDialog { showingSource = false }
}

@Composable
private fun SourceAddressDialog(onDismiss: () -> Unit) {
    val qrCode = remember {
        val size = 384
        val matrix = QRCodeWriter().encode(SOURCE_URL, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { index ->
            if (matrix[index % size, index / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val qrSize = LocalConfiguration.current.screenWidthDp.dp * 0.5f
    Dialog(visible = true, onDismissRequest = onDismiss) {
        WearListScreen("开源地址", edgeButton = { WearBackButton(onDismiss) }) {
            item(key = "source-qr") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(qrCode, contentDescription = tr("开源仓库二维码"), modifier = Modifier.size(qrSize))
                }
            }
            item(key = "source-url") {
                Text(SOURCE_URL, modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            item(key = "source-hint") {
                Text(tr("使用手机扫码访问"), modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppIdentity() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.wraive_mark), contentDescription = null,
            modifier = Modifier.size(76.dp))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary)
        Text(Build.MODEL, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 12.dp, end = 12.dp))
    }
}

@Composable
private fun AboutSectionLabel(label: String) {
    Text(tr(label), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun AboutPanel(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(tr(title), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun DeveloperPanel() {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.developer_moye), contentDescription = tr("开发者头像"),
            contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("爅峫", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(tr("设计与开发"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChangelogBulletItem(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("• ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(tr(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
