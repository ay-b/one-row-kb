package com.example.onerow

import com.example.onerow.report.CrashReport
import com.example.onerow.report.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    private val device = CrashReport.DeviceInfo("0.25.0", 2500, "15", 35, "Google Pixel 8", "ru-UA")

    @Test fun exceptionMessagesNeverReachTheReport() {
        // messages can quote what the user typed: they must not be in the report, not even from a cause
        val e = RuntimeException("my password is hunter2", IllegalStateException("secret sentence typed by the user"))
        val r = CrashReport.build(device, 0L, e)
        assertFalse(r.contains("hunter2")); assertFalse(r.contains("secret")); assertFalse(r.contains("typed"))
        assertTrue(r.contains("java.lang.RuntimeException"))
        assertTrue(r.contains("Caused by: java.lang.IllegalStateException"))
        assertTrue(r.contains("at com.example.onerow.CrashReportTest.exceptionMessagesNeverReachTheReport"))
    }

    @Test fun headerHasOnlyTheAnnouncedFields() {
        val lines = CrashReport.build(device, 0L, RuntimeException()).lines()
        assertEquals("OneRow crash report", lines[0])
        assertEquals("App: 0.25.0 (2500)", lines[1])
        assertEquals("Android: 15 (SDK 35)", lines[2])
        assertEquals("Phone: Google Pixel 8", lines[3])
        assertEquals("Language: ru-UA", lines[4])
        assertEquals("When: 1970-01-01 00:00 UTC", lines[5])
        assertEquals("", lines[6])
    }

    @Test fun hugeStacksAreCapped() {
        val e = RuntimeException()
        e.stackTrace = Array(100) { StackTraceElement("a.B", "m$it", "B.kt", it) }
        val t = CrashReport.trace(e)
        assertEquals(CrashReport.MAX_FRAMES, t.lines().count { it.startsWith("  at ") })
        assertTrue(t.contains("... 60 more"))
    }

    @Test fun causeLoopsEndAndLongChainsAreCapped() {
        val a = RuntimeException(); val b = RuntimeException()
        a.initCause(b); b.initCause(a)
        assertEquals(2, CrashReport.trace(a).lines().count { !it.startsWith("  ") })
        var e: Throwable = RuntimeException()
        repeat(10) { e = RuntimeException(e) }
        assertEquals(CrashReport.MAX_CAUSES, CrashReport.trace(e).lines().count { !it.startsWith("  ") })
    }

    @Test fun previewIsAboutThreeHundredCharactersAndNeverSplitsACharacter() {
        val short = "x".repeat(300)
        assertEquals(short to false, CrashReport.preview(short))
        val (p, more) = CrashReport.preview("y".repeat(301))
        assertTrue(more); assertEquals(300 + 1, p.length); assertTrue(p.endsWith("…"))
        val emoji = "a".repeat(299) + "😀" + "tail"                           // a surrogate pair straddles the cut
        val (q, _) = CrashReport.preview(emoji)
        assertEquals("a".repeat(299) + "…", q)
    }

    // ---- shake ----
    private fun sample(d: ShakeDetector, t: Long, g: Float) = d.onSample(t, 0f, g * 9.81f, 0f)
    /** One jolt as a real sensor stream shows it: calm just before, then the peak. */
    private fun shake(d: ShakeDetector, t: Long, g: Float): Boolean { sample(d, t - 60, 1.0f); return sample(d, t, g) }

    @Test fun threeHardJoltsAreAShake() {
        val d = ShakeDetector()
        assertFalse(shake(d, 0, 3.2f)); assertFalse(shake(d, 300, 3.2f)); assertTrue(shake(d, 600, 3.2f))
    }

    @Test fun twoJoltsGravityAndWalkingAreNot() {
        val d = ShakeDetector()
        assertFalse(shake(d, 0, 3.2f)); assertFalse(shake(d, 300, 3.2f))
        val w = ShakeDetector()
        for (i in 0 until 400) assertFalse(shake(w, i * 20L, 1.0f))                    // phone lying still
        for (i in 0 until 40) assertFalse(shake(w, 10_000 + i * 500L, 2.2f))           // a brisk walk: hard but below the threshold
    }

    @Test fun oneJoltSampledManyTimesIsOneJolt() {
        val d = ShakeDetector()
        for (i in 0 until 30) assertFalse(sample(d, i * 20L, 3.5f))                     // 600 ms of one long push or a vibrating ride
    }

    @Test fun slowJoltsOutsideTheWindowAreNot() {
        val d = ShakeDetector()
        assertFalse(shake(d, 0, 3.2f)); assertFalse(shake(d, 1000, 3.2f)); assertFalse(shake(d, 2000, 3.2f))
    }

    @Test fun cooldownThenAgain() {
        val d = ShakeDetector()
        shake(d, 0, 3.2f); shake(d, 300, 3.2f); assertTrue(shake(d, 600, 3.2f))
        assertFalse(shake(d, 900, 3.2f)); assertFalse(shake(d, 1200, 3.2f)); assertFalse(shake(d, 1500, 3.2f))
        assertFalse(shake(d, 5000, 3.2f)); assertFalse(shake(d, 5300, 3.2f)); assertTrue(shake(d, 5600, 3.2f))
    }
}
