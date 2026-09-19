package moye.wear.wraive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import moye.wear.wraive.ui.theme.LocalScreenRound
import moye.wear.wraive.ui.tr

@Composable
internal fun Watch5ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(visible = visible, onDismissRequest = onDismiss) {
        val state = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()
        val isRound = LocalScreenRound.current
        ScreenScaffold(scrollState = state) { contentPadding ->
            // AlertDialog's internal list does not expose its rotary haptic setting.
            TransformingLazyColumn(
                state = state,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(
                    scrollableState = state,
                    hapticFeedbackEnabled = false
                )
            ) {
                with(WearFisheyeScope(this, transformationSpec, isRound)) {
                    item {
                        Text(
                            text = tr(title),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    item {
                        Text(
                            text = tr(message),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            AlertDialogDefaults.DismissButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = tr("取消"))
                            }
                            AlertDialogDefaults.ConfirmButton(
                                onClick = onConfirm,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = tr(confirmLabel))
                            }
                        }
                    }
                }
            }
        }
    }
}
