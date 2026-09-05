package moye.wear.wraive.chat

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal object ExpressionEvaluator {
    fun evaluate(expression: String): String {
        require(expression.length <= 512) { "Expression is too long" }
        val value = Parser(expression).parse()
        require(value.isFinite()) { "Result is not finite" }
        val rounded = value.toLong()
        return if (value == rounded.toDouble()) rounded.toString() else
            "%.12g".format(java.util.Locale.US, value)
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): Double {
            val result = expression()
            whitespace()
            require(index == source.length) { "Unexpected '${source[index]}' at ${index + 1}" }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                whitespace()
                value = when {
                    consume('+') -> value + term()
                    consume('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                whitespace()
                value = when {
                    consume('*') -> value * power()
                    consume('/') -> value / power()
                    consume('%') -> value % power()
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            whitespace()
            return if (consume('^')) base.pow(power()) else base
        }

        private fun unary(): Double {
            whitespace()
            return when {
                consume('+') -> unary()
                consume('-') -> -unary()
                else -> primary()
            }
        }

        private fun primary(): Double {
            whitespace()
            if (consume('(')) {
                val value = expression()
                whitespace()
                require(consume(')')) { "Missing ')'" }
                return value
            }
            if (index < source.length && (source[index].isLetter() || source[index] == 'π')) {
                val name = identifier().lowercase()
                return when (name) {
                    "pi", "π" -> PI
                    "e" -> E
                    else -> {
                        whitespace()
                        require(consume('(')) { "Function $name requires parentheses" }
                        val value = expression()
                        whitespace()
                        require(consume(')')) { "Missing ')' after $name" }
                        when (name) {
                            "sqrt" -> sqrt(value)
                            "abs" -> abs(value)
                            "sin" -> sin(value)
                            "cos" -> cos(value)
                            "tan" -> tan(value)
                            "ln" -> ln(value)
                            "log", "log10" -> log10(value)
                            else -> error("Unknown function $name")
                        }
                    }
                }
            }
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] in ".eE+-")) {
                val character = source[index]
                if ((character == '+' || character == '-') && index > start && source[index - 1] !in "eE") break
                index++
            }
            require(index > start) { "Expected a number at ${index + 1}" }
            return source.substring(start, index).toDoubleOrNull()
                ?: error("Invalid number at ${start + 1}")
        }

        private fun identifier(): String {
            val start = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == 'π')) index++
            return source.substring(start, index)
        }

        private fun whitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun consume(character: Char): Boolean {
            if (index >= source.length || source[index] != character) return false
            index++
            return true
        }
    }
}
