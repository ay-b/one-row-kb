package com.example.onerow.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.example.onerow.BuildConfig
import com.example.onerow.Storage

/** Product ids to create in the Play Console (see docs/billing.md). */
object Products {
    const val MONTHLY = "monthly"          // subscription, EUR 1 / month, with a 1-month free-trial offer
    const val LIFETIME = "lifetime_v1"     // one-time product, EUR 10, "forever for version 1"
}

/** The saved answer of Google Play, and the rules applied to it. */
object BillingStore {
    private const val FILE = "onerow_billing"

    fun load(c: Context): Snapshot {
        val p = Storage.prefs(c, FILE)
        return Snapshot(p.getBoolean("lifetime", false), p.getBoolean("sub", false), p.getBoolean("renew", false),
            p.getLong("subTime", 0L), p.getLong("subEnd", 0L), p.getLong("checked", 0L))
    }

    fun save(c: Context, s: Snapshot) {
        Storage.prefs(c, FILE).edit().putBoolean("lifetime", s.lifetime).putBoolean("sub", s.subActive)
            .putBoolean("renew", s.subAutoRenewing).putLong("subTime", s.subPurchaseTime).putLong("subEnd", s.subEnd).putLong("checked", s.checkedAt).apply()
    }

    private fun installTime(c: Context): Long = try { c.packageManager.getPackageInfo(c.packageName, 0).firstInstallTime } catch (e: Exception) { 0L }

    /** What the keyboard may do now. Debug builds can force a state: `adb shell run-as <pkg> ...` writes "debug_force" (locked | full | expiring) to the prefs file. */
    fun access(c: Context, now: Long = System.currentTimeMillis()): Access {
        if (BuildConfig.DEBUG) when (Storage.prefs(c, FILE).getString("debug_force", null)) {
            "locked" -> return Access.Locked
            "full" -> return Access.Full(Reason.LIFETIME)
            "expiring" -> return Access.Full(Reason.SUBSCRIBED, expiresInDays = 5)
        }
        return Entitlement.evaluate(now, installTime(c), load(c))
    }

    fun lastChecked(c: Context) = load(c).checkedAt
}

/**
 * Talks to Google Play Billing: reads what the user owns (and keeps it in [BillingStore]), loads prices, starts purchases.
 * Only the settings screen starts a purchase; the keyboard just reads the saved answer and asks for a refresh now and then.
 */
class BillingManager(context: Context, private val onChange: () -> Unit) : PurchasesUpdatedListener {
    private val app = context.applicationContext
    private var client: BillingClient? = null
    var monthly: ProductDetails? = null; private set
    var lifetime: ProductDetails? = null; private set

    private fun ready(then: (BillingClient) -> Unit) {
        val c = client ?: BillingClient.newBuilder(app).setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build().also { client = it }
        if (c.isReady) { then(c); return }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) { if (r.responseCode == BillingClient.BillingResponseCode.OK) then(c) }
            override fun onBillingServiceDisconnected() { }
        })
    }

    /** Looks at what is owned now (and the prices); results land in [BillingStore] and [onChange] is called. */
    fun refresh(withPrices: Boolean = false) = ready { c ->
        queryOwned(c)
        if (withPrices) queryPrices(c)
    }

    private fun queryOwned(c: BillingClient) {
        var inapp: List<Purchase>? = null; var subs: List<Purchase>? = null
        fun done() {
            val i = inapp ?: return; val s = subs ?: return
            apply(c, i + s)
        }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            inapp = if (r.responseCode == BillingClient.BillingResponseCode.OK) list else null; if (inapp != null) done()
        }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { r, list ->
            subs = if (r.responseCode == BillingClient.BillingResponseCode.OK) list else null; if (subs != null) done()
        }
    }

    private fun apply(c: BillingClient, purchases: List<Purchase>) {
        var life = false; var sub: Purchase? = null
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue            // pending ones do not count yet
            if (!p.isAcknowledged) c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
            if (Products.LIFETIME in p.products) life = true
            if (Products.MONTHLY in p.products) sub = p
        }
        val now = System.currentTimeMillis()
        val renewing = sub?.isAutoRenewing ?: false
        val old = BillingStore.load(app)
        // a cancelled subscription ends at the next monthly anniversary; worked out once, when the cancellation is first seen
        val end = if (sub == null || renewing) 0L else if (!old.subAutoRenewing || old.subEnd <= now) Entitlement.nextPeriodEnd(sub.purchaseTime, now) else old.subEnd
        BillingStore.save(app, Snapshot(life, sub != null, renewing, sub?.purchaseTime ?: 0L, end, now))
        onChange()
    }

    private fun queryPrices(c: BillingClient) {
        fun q(id: String, type: String, set: (ProductDetails?) -> Unit) {
            val prod = QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
            c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(prod)).build()) { r, list ->
                if (r.responseCode == BillingClient.BillingResponseCode.OK) { set(list.firstOrNull()); onChange() }
            }
        }
        q(Products.MONTHLY, BillingClient.ProductType.SUBS) { monthly = it }
        q(Products.LIFETIME, BillingClient.ProductType.INAPP) { lifetime = it }
    }

    /** The free-trial offer when this user may still have it, else the plain one. */
    private fun subOffer(pd: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = pd.subscriptionOfferDetails ?: return null
        return offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros == 0L } ?: offers.firstOrNull()
    }

    /** The monthly price ("1,00 EUR"), and whether a free trial comes first. */
    fun monthlyPrice(): Pair<String, Boolean>? {
        val pd = monthly ?: return null
        val o = subOffer(pd) ?: return null
        val phases = o.pricingPhases.pricingPhaseList
        return phases.last().formattedPrice to (phases.first().priceAmountMicros == 0L && phases.size > 1)
    }

    fun lifetimePrice(): String? = lifetime?.oneTimePurchaseOfferDetails?.formattedPrice

    fun buyMonthly(a: Activity) {
        val pd = monthly ?: return; val o = subOffer(pd) ?: return
        launch(a, BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).setOfferToken(o.offerToken).build())
    }

    fun buyLifetime(a: Activity) {
        val pd = lifetime ?: return
        launch(a, BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build())
    }

    private fun launch(a: Activity, p: BillingFlowParams.ProductDetailsParams) = ready { c ->
        c.launchBillingFlow(a, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p)).build())
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        if (r.responseCode == BillingClient.BillingResponseCode.OK) refresh()      // read everything again: simplest and always right
    }

    fun close() { client?.endConnection(); client = null }
}
