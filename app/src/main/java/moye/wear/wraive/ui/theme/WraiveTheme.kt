package moye.wear.wraive.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import androidx.wear.compose.material3.dynamicColorScheme
import moye.wear.wraive.ui.LocalUiLocaleTag

val LocalScreenRound = staticCompositionLocalOf { true }

private val fallbackScheme = ColorScheme(
    primary = Color(0xFFC8B6FF),
    primaryDim = Color(0xFFB29AF7),
    onPrimary = Color(0xFF2F1D5B),
    primaryContainer = Color(0xFF493575),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCAC2DC),
    secondaryDim = Color(0xFFAEA6BF),
    onSecondary = Color(0xFF312E3B),
    secondaryContainer = Color(0xFF484451),
    onSecondaryContainer = Color(0xFFE7E0F4),
    tertiary = Color(0xFFFFB2C7),
    tertiaryDim = Color(0xFFE595AC),
    onTertiary = Color(0xFF511D31),
    tertiaryContainer = Color(0xFF6D3348),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFE9E1EC),
    onSurface = Color(0xFFE9E1EC),
    onSurfaceVariant = Color(0xFFCBC3CE),
    outline = Color(0xFF958E99),
    outlineVariant = Color(0xFF4A454E),
    surfaceContainerLow = Color(0xFF1B181D),
    surfaceContainer = Color(0xFF211E23),
    surfaceContainerHigh = Color(0xFF2C292E)
)

@Composable
fun WraiveTheme(
    dynamicColor: Boolean = true,
    customFontPath: String = "",
    localeTag: String = "system",
    content: @Composable () -> Unit
) {
    val scheme = if (dynamicColor) {
        dynamicColorScheme(LocalContext.current) ?: fallbackScheme
    } else {
        fallbackScheme
    }
    val fontFamily = androidx.compose.runtime.remember(customFontPath) {
        customFontPath.takeIf(String::isNotBlank)?.let { path ->
            runCatching { FontFamily(Typeface.createFromFile(path)) }.getOrNull()
        } ?: FontFamily.Default
    }
    CompositionLocalProvider(
        LocalScreenRound provides LocalConfiguration.current.isScreenRound,
        LocalUiLocaleTag provides localeTag
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(defaultFontFamily = fontFamily),
            content = content
        )
    }
}
