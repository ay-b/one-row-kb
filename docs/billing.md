# Payments: Google Play Billing

Rules: the first 14 days after installing are free; after that the user starts the subscription's 1-month free trial,
subscribes (EUR 1 a month, renews by itself) or buys the keyboard forever for version 1 (EUR 10). Otherwise the keyboard
locks: plain typing only, and the guess bar says "Trial has expired, please subscribe" with a gear that opens the settings.
A cancelled subscription keeps working until its period ends; before anything is typed the guess bar counts the days down.

Code: `billing/Entitlement.kt` (the rules, unit-tested), `billing/Billing.kt` (Play Billing, the saved answer),
the Subscription section in `MainActivity`, and the lock in `KeyboardView.locked` / `OneRowService.syncAccess`.
`FREE_DAYS` (14) and `CACHE_TRUST_DAYS` (35) are in `Entitlement`.

## Play Console setup (once)

App id: `one.yb.onerow`.
1. **Subscription** `monthly`: one base plan, auto-renewing, billing period 1 month, price EUR 1.00. Add an **offer** to it with a
   **free trial phase of 1 month**, eligibility "new customer acquisition". (A user who used the trial gets the plain plan.)
2. **One-time product** `lifetime_v1`: EUR 10.00. For version 2 create `lifetime_v2`; owners of v1 keep v1.
3. Upload a signed release build to the internal testing track, add license testers (Settings > License testing), and
   install through the test link: Billing only works for apps installed from Play.
4. Fill in the Data safety form and a privacy policy; the app itself needs no internet permission (Play handles the network).

## Testing without Play

Debug builds accept a forced state in the saved answer: set `debug_force` to `locked`, `full` or `expiring` in
`/data/user_de/0/one.yb.onerow/shared_prefs/onerow_billing.xml` (a `<string name="debug_force">locked</string>` entry; restart the
keyboard). Release builds ignore it.

## Notes

- The phone is never told the exact end of a cancelled subscription (that needs a server using the Play Developer API). The
  countdown uses the next monthly anniversary of the purchase, which is when renewals happen.
- Play requires new apps to target a recent Android API level; check the current rule before the first upload (targetSdk is 34 now).
