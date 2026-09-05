package moye.wear.wraive.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.Text
import moye.wear.wraive.ui.tr

@Composable
fun WearCheckboxButton(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    CheckboxButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        label = { Text(tr(label), style = MaterialTheme.typography.labelMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis) }
    )
}

@Composable
fun WearSelectionButton(
    label: String,
    onClick: () -> Unit,
    selected: Boolean,
    secondary: String? = null,
    enabled: Boolean = true
) {
    RadioButton(
        selected = selected,
        onSelect = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        label = {
            Text(tr(label), style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = secondary?.takeIf(String::isNotBlank)?.let { value ->
            { Text(tr(value), style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    )
}

@Composable
fun <T> WearSelectionField(
    label: String,
    value: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String = { it.toString() }
) {
    var visible by remember { mutableStateOf(false) }
    WearActionButton(label, { visible = true }, secondary = optionLabel(value))
    WearSelectionDialog(visible, label, value, options, { visible = false }, onSelect, optionLabel)
}

@Composable
fun <T> WearSelectionDialog(
    visible: Boolean,
    title: String,
    selected: T,
    options: List<T>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String = { it.toString() }
) {
    Dialog(visible = visible, onDismissRequest = onDismiss) {
        WearListScreen(title, modifier = Modifier.selectableGroup(), edgeButton = {
            EdgeButton(onClick = onDismiss) { Text(tr("取消")) }
        }) {
            options.forEachIndexed { index, option ->
                item(key = "option-$index") {
                    WearSelectionButton(optionLabel(option), {
                        onSelect(option)
                        onDismiss()
                    }, selected == option)
                }
            }
        }
    }
}
