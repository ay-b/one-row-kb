package com.example.onerow.engine

import java.math.BigDecimal
import java.math.MathContext

/**
 * The keypad's calculator: + - * / and %, typed the way it is written. "%" works like on a phone calculator:
 * 50% is 0.5, 200+10% is 220 (a percent of the left side), 200*10% is 20.
 */
object Calc {
    private const val OPS = "+-*/%"

    /** True when [s] holds something to calculate: an operator after the first character. */
    fun worthIt(s: String) = s.indexOfAny(OPS.toCharArray(), 1) > 0 || s.endsWith("%")

    /** The value of [expr], or null when it makes no arithmetic sense (a trailing operator is ignored). */
    fun eval(expr: String): Double? {
        var s = norm(expr).trimEnd('+', '-', '*', '/', '(')
        if (s.isEmpty()) return null
        val p = Parser(s)
        val v = try { p.expression().also { if (p.i != s.length) return null } } catch (e: IllegalStateException) { return null }
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    /** Typed variants of the operators count as the plain ones. */
    private fun norm(e: String) = e.replace(',', '.')

    /** [v] as typed text: no trailing zeros, at most 10 significant digits; [comma] picks the decimal sign. */
    fun format(v: Double, comma: Boolean): String {
        var t = BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
        if (t == "-0") t = "0"
        return if (comma) t.replace('.', ',') else t
    }

    private class Parser(val s: String) {
        var i = 0
        private var pctOnly = false                       // the last term was just "number%"

        fun expression(): Double {
            var left = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                var right = term()
                if (pctOnly) right = left * right          // 200+10% -> 200 + 20
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        private fun term(): Double {
            pctOnly = false
            var left = unary()
            var single = pctOnly
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val right = unary()
                if (op == '/' && right == 0.0) error("division by zero")
                left = if (op == '*') left * right else left / right
                single = false
            }
            pctOnly = single
            return left
        }

        private fun unary(): Double {
            if (i < s.length && (s[i] == '-' || s[i] == '+')) { val neg = s[i++] == '-'; val v = unary(); pctOnly = false; return if (neg) -v else v }
            if (i < s.length && s[i] == '(') {
                i++
                val v = expression()
                if (i < s.length && s[i] == ')') i++ else if (i < s.length) error("')' expected")   // a missing ')' at the end is forgiven
                pctOnly = false
                return v
            }
            return number()
        }

        private fun number(): Double {
            val st = i
            var dots = 0
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) { if (s[i] == '.') dots++; i++ }
            if (i == st || dots > 1 || (i - st == 1 && dots == 1)) error("number expected")
            var v = s.substring(st, i).toDouble()
            pctOnly = false
            while (i < s.length && s[i] == '%') { v /= 100.0; i++; pctOnly = true }
            return v
        }
    }
}
