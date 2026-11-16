package com.example.onerow.billing

import java.util.Calendar

/** What the keyboard may do right now. */
sealed class Access {
    /** Everything works. [freeDaysLeft] > 0 in the free first weeks; [expiresInDays] is set when a subscription was cancelled and will lapse. */
    data class Full(val reason: Reason, val freeDaysLeft: Int = 0, val expiresInDays: Int? = null) : Access()
    /** Plain typing only; the guess bar asks to subscribe. */
    object Locked : Access()
}

enum class Reason { LIFETIME, SUBSCRIBED, FREE_PERIOD }

/** What the last look at Google Play found (kept on the phone, so typing never waits for the network). */
data class Snapshot(
    val lifetime: Boolean = false,           // bought "forever for v1"
    val subActive: Boolean = false,          // a subscription (or its free trial) is running
    val subAutoRenewing: Boolean = false,
    val subPurchaseTime: Long = 0L,
    val subEnd: Long = 0L,                   // for a cancelled subscription: when its period ends (worked out when the cancellation was first seen)
    val checkedAt: Long = 0L,                // when Play last answered; 0 = never
)

/**
 * The rules:
 *  - bought forever (v1): full access, for good;
 *  - a running subscription (its free trial included): full access; one that was cancelled keeps working until its
 *    period ends, and [Access.Full.expiresInDays] counts that down;
 *  - the first [FREE_DAYS] days after installing: full access, free;
 *  - otherwise locked.
 */
object Entitlement {
    const val FREE_DAYS = 14
    /** A subscription seen this long ago is still believed (a month renews it); after that Play must confirm it again. */
    const val CACHE_TRUST_DAYS = 35
    private const val DAY = 24L * 60 * 60 * 1000

    fun evaluate(now: Long, installTime: Long, s: Snapshot): Access {
        if (s.lifetime) return Access.Full(Reason.LIFETIME)
        if (s.subActive && now - s.checkedAt <= CACHE_TRUST_DAYS * DAY) {
            if (s.subAutoRenewing) return Access.Full(Reason.SUBSCRIBED)
            if (s.subEnd > now) return Access.Full(Reason.SUBSCRIBED, expiresInDays = daysUntil(s.subEnd, now))
        }
        val sinceInstall = now - installTime
        if (installTime > 0 && sinceInstall < FREE_DAYS * DAY)
            return Access.Full(Reason.FREE_PERIOD, freeDaysLeft = daysUntil(installTime + FREE_DAYS * DAY, now))
        return Access.Locked
    }

    /** Whole days left until [end], counting a started day (1 .. n). */
    fun daysUntil(end: Long, now: Long): Int = (((end - now) + DAY - 1) / DAY).toInt().coerceAtLeast(1)

    /**
     * When the current monthly period of a subscription bought at [purchaseTime] ends: the next monthly anniversary after [now].
     * (The phone is not told the exact end of a cancelled subscription; renewals fall on that anniversary.)
     */
    fun nextPeriodEnd(purchaseTime: Long, now: Long): Long {
        if (purchaseTime <= 0) return 0L
        val c = Calendar.getInstance().apply { timeInMillis = purchaseTime }
        var guard = 0
        while (c.timeInMillis <= now && guard++ < 1200) c.add(Calendar.MONTH, 1)
        return c.timeInMillis
    }
}
