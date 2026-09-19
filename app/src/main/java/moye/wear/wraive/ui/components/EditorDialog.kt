package moye.wear.wraive.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import moye.wear.wraive.compat.DeviceCompatibility
import moye.wear.wraive.ui.tr

@Composable
fun ConfirmActionDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (DeviceCompatibility.isXiaomiWatch5) {
        Watch5ConfirmDialog(visible, title, message, confirmLabel, onDismiss, onConfirm)
        return
    }
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = { Text(tr(title)) },
        text = { Text(tr(message), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = onConfirm,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = tr(confirmLabel))
            }
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = tr("取消"))
            }
        }
    )
}

@Composable
fun FullScreenEditor(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    content: WearFisheyeScope.() -> Unit
) {
    Dialog(visible = visible, onDismissRequest = onDismiss) {
        WearListScreen(
            title = title,
            edgeButton = {
                EdgeButton(onClick = onSave, enabled = saveEnabled) { Text(tr("保存")) }
            }
        ) {
            content()
            item(key = "editor-cancel") {
                WearActionButton("取消", onDismiss, icon = Icons.Default.Close)
            }
        }
    }
}

@Composable
fun WearTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLines: Int = 3,
    password: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(tr(label), style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis) },
        textStyle = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
    )
}
