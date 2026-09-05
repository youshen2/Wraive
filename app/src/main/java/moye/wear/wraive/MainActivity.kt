package moye.wear.wraive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import moye.wear.wraive.ui.navigation.AppNavigation
import moye.wear.wraive.ui.theme.WraiveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as WraiveApplication).graph
        setContent {
            val preferences by graph.preferences.preferences.collectAsStateWithLifecycle()
            WraiveTheme(
                dynamicColor = preferences.dynamicColor,
                customFontPath = preferences.customFontPath,
                localeTag = preferences.localeTag
            ) {
                AppScaffold { AppNavigation(graph) }
            }
        }
    }
}
