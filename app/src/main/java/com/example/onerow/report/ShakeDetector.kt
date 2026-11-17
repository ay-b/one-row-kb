package com.example.onerow.report

import kotlin.math.sqrt

/**
 * Decides whether a stream of accelerometer samples is a deliberate shake: [needed] hard jolts (above [thresholdG], gravity included)
 * inside [windowMs], then silent for [cooldownMs]. A jolt counts once, when the signal rises above the threshold from below it, so a
 * long vibration is not a shake. Walking and normal typing stay far below the threshold. Pure logic, no Android.
 */
class ShakeDetector(
    private val thresholdG: Float = 2.7f,
    private val needed: Int = 3,
    private val windowMs: Long = 1500,
    private val cooldownMs: Long = 4000,
    private val minGapMs: Long = 120,
) {
    private val peaks = ArrayDeque<Long>()
    private var lastFire: Long? = null
    private var armed = true

    /** One sample (m/s^2) at time [tMs]; true when a shake has just been recognised. */
    fun onSample(tMs: Long, x: Float, y: Float, z: Float): Boolean {
        val g = sqrt(x * x + y * y + z * z) / 9.81f
        if (g < thresholdG) { armed = true; return false }
        if (!armed) return false                                                        // still the same jolt
        armed = false
        if (peaks.isNotEmpty() && tMs - peaks.last() < minGapMs) return false          // the same jolt, sampled again
        peaks.addLast(tMs)
        while (peaks.isNotEmpty() && tMs - peaks.first() > windowMs) peaks.removeFirst()
        val last = lastFire
        if (peaks.size >= needed && (last == null || tMs - last >= cooldownMs)) { peaks.clear(); lastFire = tMs; return true }
        return false
    }
}
