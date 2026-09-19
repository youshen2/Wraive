package moye.wear.wraive.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.rotary.RotaryScrollableBehavior
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import moye.wear.wraive.compat.DeviceCompatibility

@Composable
fun rememberCompatibleRotaryBehavior(scrollableState: ScrollableState): RotaryScrollableBehavior =
    RotaryScrollableDefaults.behavior(
        scrollableState = scrollableState,
        hapticFeedbackEnabled = DeviceCompatibility.supportsWearRotaryHaptics
    )
