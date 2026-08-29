package com.coinepro.feature.alerts

/**
 * The alert centre's actions, named and in order, in one place a test can hold on to.
 *
 * ### Why an enum instead of five composables in a column
 *
 * They *were* five composables in a column, and the order lived in the source of the sheet where
 * nothing could see it. The complaint this answers is the most repeated one in the negative reviews
 * of this whole category of app, and it is not about any single feature: **the controls move**.
 * Somebody who has pressed "the third one down" a hundred times without reading it presses it once
 * after a release and deletes an alert. That is not a design mistake anybody makes deliberately —
 * it is what happens when the order of a menu is an accident of where the lines happen to sit.
 *
 * So the order is data, the screen renders it in this order and cannot render it in another, and
 * [AlertCenterActionsTest] asserts both the identity of the set and its sequence. A future change
 * to either has to change this file, which fails a test, rather than surprising somebody's thumb.
 *
 * ### The order itself, and why the destructive one is last
 *
 * Read, then change, then copy, then destroy. «تاریخچه» leads because the question a reader has
 * about an alert they can already read on its row is "did it work", and the history is the only
 * thing that answers it. «حذف» is last because it is the one action with no undo — this screen asks
 * before it, and putting it anywhere but the end would put it under a thumb reaching for something
 * reversible.
 *
 * ### [id] is not for display
 *
 * It is the stable name the test pins and nothing else; the Persian label is a string resource, as
 * all product copy here is. Renaming a label is a translation. Renaming an id is a change to what
 * the menu *is*, and it should read like one in a diff.
 */
enum class AlertCenterAction(val id: String) {
    /** The audit log for this alert. The answer to «did it fire and was I told». */
    HISTORY("history"),

    /** Switch it off, or back on. Reversible from the toaster. */
    PAUSE("pause"),

    /** Re-open it in the editor. Absent where the editor cannot express the alert. */
    EDIT("edit"),

    /** Copy it, armed and unfired. Absent when the store would refuse another. */
    DUPLICATE("duplicate"),

    /** Remove it. The only one that asks first, and the only one with no undo. */
    DELETE("delete"),
}

/** Which of [AlertCenterAction] one row actually offers, and the one action the header carries. */
object AlertCenterActions {

    /**
     * The header's actions, in order.
     *
     * Pinned beside the menu because it is the same promise, and pinned as a *list* because there
     * are two of them: «هشدار تازه» — the reason anybody opens this screen — and the webhook sheet,
     * which is where an alert's other delivery channel is made. A third appearing here, or these
     * two swapping, is exactly the churn this file exists to catch, and it now has to be a change
     * to this line and a change to a test rather than a line moved in a layout.
     *
     * The labels are `R.string.alerts_new` and `R.string.webhooks_title`; these are the identities.
     */
    val PRIMARY: List<String> = listOf("new_alert", "webhooks")

    /**
     * The actions offered for one row, in [AlertCenterAction]'s own order.
     *
     * Two are conditional and both are **hidden rather than dimmed**. A disabled row invites the
     * reader to work out what would enable it, and in each case nothing would:
     *
     * * «ویرایش» is absent for an alert this sheet cannot express — a 24-hour-change condition, a
     *   nested compound — and for a server alert, whose route has no update at all: changing one
     *   would mean deleting it and creating another, and a failure between the two would leave the
     *   reader with neither the old alert nor the new one.
     * * «تکثیر» is absent at the store's cap, where a copy would simply be refused.
     *
     * Filtering rather than reordering, so that whichever actions a row does offer are always in
     * the same sequence as every other row's.
     */
    fun forRow(
        row: AlertRow,
        canDuplicate: Boolean,
        editable: Boolean = row.venue == AlertVenue.DEVICE && AlertDraft.of(row.alert) != null,
    ): List<AlertCenterAction> = AlertCenterAction.entries.filter { action ->
        when (action) {
            AlertCenterAction.EDIT -> editable
            AlertCenterAction.DUPLICATE -> canDuplicate && row.venue == AlertVenue.DEVICE
            else -> true
        }
    }
}
