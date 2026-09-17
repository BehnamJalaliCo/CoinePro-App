package com.coinepro.core.common

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Both states of the two switches phase Φ is about** (run ΤΦΥ, F5 and F8).
 *
 * The point of this file is the branch nobody is looking at. A shipping build has forex trading off
 * and everything unlocked, so the *other* half of every condition in the app — the broker links, the
 * copy-trading door, every tier wall — is code that compiles and never runs. A `const val false`
 * would make that half unreachable and it would rot; a flag that is driven both ways here does not.
 *
 * The flags are restored after every test, because they are process-wide by design and a test that
 * left one turned over would hand the next one a different app.
 */
class EntitlementGateTest {

    @After
    fun restore() = FeatureFlags.reset()

    @Test
    fun `a shipping build offers no forex account and no walls`() {
        FeatureFlags.reset()
        assertFalse("this build offers a way to trade forex", FeatureFlags.forexTrading)
        assertTrue("this build has walls in it", FeatureFlags.allUnlocked)
        assertTrue(Entitlements.all)
        assertTrue(Entitlements.attemptsLockedContent)
        assertTrue(Entitlements.foundingMember)
    }

    @Test
    fun `turning the walls back on is one word`() {
        FeatureFlags.allUnlocked = false
        assertFalse(Entitlements.all)
        // The two derived answers move with it rather than being set separately, which is the whole
        // reason they are derived: three switches that mean one thing drift, and the day one of
        // them is wrong is a day a reader is told the app is free in front of a wall.
        assertFalse(Entitlements.attemptsLockedContent)
        assertFalse(Entitlements.foundingMember)
    }

    @Test
    fun `turning forex trading back on does not open the walls, and the reverse`() {
        // The two are independent and must stay so: «we sell nothing» and «we introduce brokers» are
        // different decisions, and a build could reasonably be either without the other.
        FeatureFlags.forexTrading = true
        assertTrue(Entitlements.all)
        FeatureFlags.allUnlocked = false
        assertTrue(FeatureFlags.forexTrading)
        assertFalse(Entitlements.all)
    }

    @Test
    fun `a silence from the server leaves the app open`() {
        // **Run Τ2, B2.** The failure mode this asymmetry exists to prevent: no route yet, a first
        // launch with no signal, a body that would not parse — every one of them arrives here as
        // null, and null must never read as «locked». Wrongly open costs the owner a subscription
        // they are giving away; wrongly closed costs a reader the product they installed.
        FeatureFlags.allUnlocked = false
        Entitlements.applyAtStart(null)
        assertTrue("a server that said nothing closed the app", Entitlements.all)
    }

    @Test
    fun `what the server served is what the next launch starts with`() {
        Entitlements.applyAtStart(false)
        assertFalse("the served refusal was ignored", Entitlements.all)
        // And every derived answer moves with it, exactly as the local switch does — the served
        // value goes through `FeatureFlags`, so there is one path and not two.
        assertFalse(Entitlements.attemptsLockedContent)
        assertFalse(Entitlements.foundingMember)
        Entitlements.applyAtStart(true)
        assertTrue(Entitlements.all)
    }

    @Test
    fun `reset puts back exactly what a shipping build starts with`() {
        FeatureFlags.forexTrading = true
        FeatureFlags.allUnlocked = false
        FeatureFlags.reset()
        assertFalse(FeatureFlags.forexTrading)
        assertTrue(FeatureFlags.allUnlocked)
    }
}
