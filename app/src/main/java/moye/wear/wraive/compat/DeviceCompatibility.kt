package moye.wear.wraive.compat

import android.os.Build

object DeviceCompatibility {
    val isXiaomiWatch5: Boolean = Build.MODEL in setOf("M2505W1", "M2501W1")
    val supportsWearRotaryHaptics: Boolean = !isXiaomiWatch5
    val supportsSwipeToDismiss: Boolean = !isXiaomiWatch5
}
