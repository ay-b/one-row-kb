package com.example.onerow

import com.example.onerow.billing.*
import org.junit.Assert.*
import org.junit.Test

class EntitlementTest {
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_700_000_000_000L          // an arbitrary "install" time
    private fun ev(daysAfterInstall: Double, s: Snapshot = Snapshot()) = Entitlement.evaluate(t0 + (daysAfterInstall * day).toLong(), t0, s)

    @Test fun freeFortnight() {
        assertEquals(Access.Full(Reason.FREE_PERIOD, freeDaysLeft = 14), ev(0.0))
        assertEquals(Access.Full(Reason.FREE_PERIOD, freeDaysLeft = 1), ev(13.5))
        assertEquals(Access.Locked, ev(14.0))
        assertEquals(Access.Locked, ev(100.0))
    }

    @Test fun lifetimeIsForever() { assertEquals(Access.Full(Reason.LIFETIME), ev(5000.0, Snapshot(lifetime = true))) }

    @Test fun renewingSubscription() {
        val s = Snapshot(subActive = true, subAutoRenewing = true, subPurchaseTime = t0 + 20 * day, checkedAt = t0 + 40 * day)
        assertEquals(Access.Full(Reason.SUBSCRIBED), ev(41.0, s))
        assertEquals(Access.Locked, ev(80.0, s))                       // not confirmed by Play for over 35 days
    }

    @Test fun cancelledSubscriptionCountsDown() {
        val bought = t0 + 20 * day
        val end = Entitlement.nextPeriodEnd(bought, bought + 2 * day)
        val s = Snapshot(subActive = true, subAutoRenewing = false, subPurchaseTime = bought, subEnd = end, checkedAt = bought + 2 * day)
        val a = ev(25.0, s)
        assertTrue(a is Access.Full && a.expiresInDays != null && a.expiresInDays!! in 1..31)
        assertEquals(Access.Locked, Entitlement.evaluate(end + day, t0, s))        // period over, no free days left
    }

    @Test fun periodEndIsNextMonthlyAnniversary() {
        val bought = 1_700_000_000_000L
        val end = Entitlement.nextPeriodEnd(bought, bought + 40 * day)       // two months after the purchase
        assertTrue(end > bought + 40 * day && end <= bought + 63 * day)
    }

    @Test fun neverCheckedAfterFreeIsLocked() { assertEquals(Access.Locked, ev(30.0, Snapshot(subActive = true, checkedAt = 0))) }
}
