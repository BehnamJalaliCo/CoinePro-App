package com.coinepro.app

import com.coinepro.core.account.EntitlementsGateway
import com.coinepro.core.common.Entitlements
import com.coinepro.core.datastore.EntitlementStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Reads the entitlement at start, then goes and asks for the next one (run Τ2, B2).
 *
 * ### The order matters, and it is the whole class
 *
 * 1. **Apply what is stored.** Whatever the server served the last time the app ran is put into
 *    `FeatureFlags` as the first thing the process does after the crash handler. Where nothing has
 *    ever been served, the local default stands — `true`, everything open.
 *
 *    It is a suspending read of the preferences file rather than a blocking one, so in principle a
 *    frame could compose before it lands. That is safe here and it is not luck: the flag decides
 *    **walls**, every wall is behind at least one tap, and the first frame of a launch is a splash
 *    or the starter screen. `Entitlements.all` is read at the gate rather than captured, so a gate
 *    reached a moment later reads the applied value. What this must not do is block `onCreate` on
 *    disk, which is the shape start-up ANRs come in.
 * 2. **Then refresh, for next time.** The answer is written to [EntitlementStore] and deliberately
 *    *not* applied to this session. `EntitlementStore`'s own note is the argument: a wall that
 *    appears while a reader is standing in the doorway reads as the app breaking.
 *
 * So the reader is at most one launch behind the server, and never surprised mid-session. Neither
 * step can close the app on a failure: step one falls back to open, step two writes nothing at all
 * unless the server answered with a boolean.
 *
 * ### Why it is not a `Worker`
 *
 * One GET of one field, on the app's own client, on a scope that dies with the process. Scheduling
 * it would mean a constraint, a backoff and a row in WorkManager's database for a request that is
 * cheaper than the launch it rides on, and the answer is not needed until the *next* launch anyway.
 */
@Singleton
class EntitlementStartUp @Inject constructor(
    private val store: EntitlementStore,
    private val gateway: EntitlementsGateway,
    private val scope: CoroutineScope,
) {
    /** Called once, from `CoineProApplication.onCreate`. */
    fun begin() {
        scope.launch {
            Entitlements.applyAtStart(store.current())
            gateway.fetch()?.let { store.serve(it) }
        }
    }
}
