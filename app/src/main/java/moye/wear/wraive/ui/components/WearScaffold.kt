package moye.wear.wraive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import moye.wear.wraive.ui.tr

val LocalWearBackAction = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun WearBackButton(onBack: () -> Unit) {
    EdgeButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回"))
    }
}

class WearFisheyeScope internal constructor(
    private val lazyScope: TransformingLazyColumnScope,
    private val transformationSpec: TransformationSpec,
    private val isRound: Boolean
) {
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable TransformingLazyColumnItemScope.() -> Unit
    ) {
        lazyScope.item(key = key, contentType = contentType) {
            FisheyeItem(transformationSpec, isRound, content)
        }
    }

    fun <T> items(
        values: List<T>,
        key: ((T) -> Any)? = null,
        contentType: ((T) -> Any?)? = null,
        content: @Composable TransformingLazyColumnItemScope.(T) -> Unit
    ) {
        values.forEach { value ->
            item(
                key = key?.invoke(value),
                contentType = contentType?.invoke(value)
            ) {
                content(value)
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.FisheyeItem(
    transformationSpec: TransformationSpec,
    isRound: Boolean,
    content: @Composable TransformingLazyColumnItemScope.() -> Unit
) {
    val itemModifier = if (isRound) {
        Modifier
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                with(transformationSpec) {
                    applyContainerTransformation(scrollProgress)
                }
            }
    } else {
        Modifier.fillMaxWidth()
    }
    Box(modifier = itemModifier) {
        this@FisheyeItem.content()
    }
}

@Composable
fun WearListScreen(
    title: String,
    modifier: Modifier = Modifier,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    scrollState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    content: WearFisheyeScope.() -> Unit
) {
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    val onBack = LocalWearBackAction.current
    val resolvedEdgeButton: (@Composable BoxScope.() -> Unit)? = edgeButton
        ?: onBack?.let { { WearBackButton(it) } }
    val listContent: @Composable BoxScope.(PaddingValues) -> Unit = { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollState)
        ) {
            with(WearFisheyeScope(this, transformationSpec, isRound)) {
                item(key = "screen-title") {
                    ListHeader(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = tr(title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                content()
            }
        }
    }
    if (resolvedEdgeButton == null) {
        ScreenScaffold(
            modifier = modifier.fillMaxSize(),
            scrollState = scrollState,
            timeText = { TimeText() },
            content = listContent
        )
    } else {
        ScreenScaffold(
            modifier = modifier.fillMaxSize(),
            scrollState = scrollState,
            timeText = { TimeText() },
            edgeButton = resolvedEdgeButton,
            content = listContent
        )
    }
}

@Composable
fun WearActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondary: String? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false
) {
    val containerColor = when {
        danger -> MaterialTheme.colorScheme.errorContainer
        primary -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        danger -> MaterialTheme.colorScheme.onErrorContainer
        primary -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            secondaryContentColor = if (primary || danger) contentColor.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = if (danger || primary) contentColor else MaterialTheme.colorScheme.primary
        ),
        icon = icon?.let { image ->
            { Icon(image, contentDescription = null, modifier = Modifier.size(24.dp)) }
        },
        label = {
            Text(tr(label), style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = secondary?.takeIf(String::isNotBlank)?.let { value ->
            {
                Text(tr(value), style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}

@Composable
fun WearSectionHeader(title: String) {
    Text(
        tr(title),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
}

@Composable
fun WearInfoCard(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(tr(title), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        Text(tr(description), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
