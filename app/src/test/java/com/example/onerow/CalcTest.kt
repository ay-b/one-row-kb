package com.example.onerow

import com.example.onerow.engine.Calc
import org.junit.Assert.*
import org.junit.Test

class CalcTest {
    private fun e(s: String) = Calc.eval(s)
    @Test fun basics() {
        assertEquals(14.0, e("2+3*4")!!, 1e-9); assertEquals(2.5, e("5/2")!!, 1e-9)
        assertEquals(-3.0, e("-5+2")!!, 1e-9); assertEquals(3.5, e("1,5+2")!!, 1e-9)
    }
    @Test fun trailingOperatorIgnored() { assertEquals(12.0, e("12+")!!, 1e-9); assertEquals(12.0, e("12*-")!!, 1e-9) }
    @Test fun nonsense() { assertNull(e("")); assertNull(e("1/0")); assertNull(e("*5")); assertNull(e("1..2")); assertNull(e("%")) }
    @Test fun percent() {
        assertEquals(0.5, e("50%")!!, 1e-9); assertEquals(220.0, e("200+10%")!!, 1e-9)
        assertEquals(180.0, e("200-10%")!!, 1e-9); assertEquals(20.0, e("200*10%")!!, 1e-9)
    }
    @Test fun format() {
        assertEquals("24", Calc.format(24.0, false)); assertEquals("0.3", Calc.format(0.1 + 0.2, false))
        assertEquals("2,5", Calc.format(2.5, true)); assertEquals("0.3333333333", Calc.format(1.0 / 3, false))
    }
    @Test fun brackets() {
        assertEquals(15.0, e("(1+2)*5")!!, 1e-9); assertEquals(-3.0, e("-(1+2)")!!, 1e-9)
        assertEquals(21.0, e("(1+2)*(3+4)")!!, 1e-9); assertEquals(3.0, e("(1+2")!!, 1e-9); assertNull(e("1+2)"))
        assertEquals(7.0, e("10-3")!!, 1e-9)
    }
    @Test fun worth() { assertFalse(Calc.worthIt("12")); assertFalse(Calc.worthIt("-5")); assertTrue(Calc.worthIt("12+")); assertTrue(Calc.worthIt("5%")) }
}
