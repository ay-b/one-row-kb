package com.example.onerow

import com.example.onerow.engine.Numbers
import org.junit.Assert.*
import org.junit.Test

class NumbersTest {
    private fun us(s: String) = Numbers.expression(s, '.', ',')
    @Test fun plainAndNegative() {
        assertEquals("3+4" to 2, us("3 apples and 4 pears"))
        assertEquals("25-5" to 2, us("25 for a kg with -5 discounted"))
        assertEquals("2026-11-16" to 3, us("2026-11-16"))
    }
    @Test fun regionalFormats() {
        assertEquals("1234.5+2" to 2, us("1,234.5 and 2"))
        assertEquals("1234,5+2" to 2, Numbers.expression("1.234,5 und 2", ',', '.'))
        assertEquals("5100+2,5" to 2, Numbers.expression("5 100 и 2,5", ',', ' '))
        assertEquals("3,14+1" to 2, Numbers.expression("3.14 и 1", ',', ' '))
    }
    @Test fun gluedToWordsIsIgnored() { assertEquals("5" to 1, us("abc12 and 5")); assertEquals("" to 0, us("no numbers")) }
}
