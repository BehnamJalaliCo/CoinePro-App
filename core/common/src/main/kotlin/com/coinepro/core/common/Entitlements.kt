package com.coinepro.core.common

/**
 * What this reader is allowed to reach (F8).
 *
 * One switch — [all] — and behind it every wall this app puts up **on its own**. It is deliberately
 * not a set of named capabilities: the product decision is «everything, for everybody, until a
 * hundred thousand installs», and modelling that as fifteen booleans would invite fourteen of them
 * to drift.
 *
 * ### What this cannot open, and why that matters
 *
 * A wall in this app is one of two things and only the first is ours:
 *
 * * **The client's own** — a saved-layout ceiling, a list cut short, a control drawn dim because the
 *   app decided a tier was too low. [all] opens these, and nothing else does.
 * * **A venue's or a server's** — the academy's per-level locks arrive on the wire; the signal
 *   list's «membership required» is a 403; the crypto venue's identity check is the venue's law.
 *   No flag in this process can open those, and a client that drew them open would be lying to the
 *   reader and then failing in front of them.
 *
 * Where a lock is the second kind and the app can usefully *try anyway*, it tries: see
 * [attemptsLockedContent]. Where it cannot, `docs/runs/RUN_TFY/BLOCKED.md` names the server that
 * owns the wall.
 *
 * ### The gating code does not move
 *
 * Every `locked`, every tier check, every wall composable stays exactly where it is. This reads as
 * one extra condition in front of each, which is why re-enabling the walls is
 * `FeatureFlags.allUnlocked = false` and not a project. `EntitlementGateTest` drives both states.
 */
object Entitlements {

    /** Whether every surface this app gates for itself is open. See the class note. */
    val all: Boolean get() = FeatureFlags.allUnlocked

    /**
     * Whether a lock the **server** declared should still be tried rather than obeyed on sight.
     *
     * True while everything is free, and it is the honest behaviour rather than a trick: a lock
     * flag on a lesson is a *claim* about what the server will do, and the only way to know is to
     * ask. A reader who tapped gets the lesson if the backend has been opened up too, and the
     * wall — with its own copy, from the server's own refusal — if it has not. What they never get
     * is a door the app painted shut on the strength of a field that may be a release out of date.
     */
    val attemptsLockedContent: Boolean get() = all

    /**
     * Whether an account created now carries the founding-member mark (F10).
     *
     * The same switch, because it is the same period: the mark means «here while it was free for
     * everybody», and the flag is exactly the definition of that period.
     */
    val foundingMember: Boolean get() = all
}
