package moye.wear.wraive.ui.components

import android.graphics.Color
import android.text.Spanned
import android.util.TypedValue
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMarkdownSmokeTest {
    @Test
    fun rendersRichMarkdownOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                14f,
                context.resources.displayMetrics
            )
            val markwon = createNativeMarkwon(context, textSizePx, Color.WHITE)
            val textView = TextView(context)
            markwon.setMarkdown(
                textView,
                """
                # Native Markdown

                **bold** and ~~removed~~ with <u>HTML</u>.

                - [x] task

                | Feature | State |
                | --- | --- |
                | Table | OK |

                ${'$'}${'$'}
                E = mc^2
                ${'$'}${'$'}
                """.trimIndent()
            )
            val rendered = textView.text.toString()
            assertTrue(rendered.contains("Native Markdown"))
            assertFalse(rendered.contains("**bold**"))
            val spanned = textView.text as Spanned
            assertTrue(spanned.getSpans(0, spanned.length, Any::class.java).size >= 6)
        }
    }
}
