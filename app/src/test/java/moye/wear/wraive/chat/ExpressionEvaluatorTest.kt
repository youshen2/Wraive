package moye.wear.wraive.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpressionEvaluatorTest {
    @Test
    fun respectsPrecedenceAndParentheses() {
        assertEquals("14", ExpressionEvaluator.evaluate("2 + 3 * 4"))
        assertEquals("20", ExpressionEvaluator.evaluate("(2 + 3) * 4"))
    }

    @Test
    fun supportsPowersConstantsAndFunctions() {
        assertEquals("512", ExpressionEvaluator.evaluate("2^3^2"))
        assertEquals("3", ExpressionEvaluator.evaluate("sqrt(9)"))
        assertEquals("1", ExpressionEvaluator.evaluate("sin(pi / 2)"))
    }

    @Test
    fun rejectsUnknownInput() {
        assertThrows(IllegalStateException::class.java) {
            ExpressionEvaluator.evaluate("runtime(1)")
        }
    }
}
