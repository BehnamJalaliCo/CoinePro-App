package com.coinepro.core.common

/**
 * A venue's own status word, in Persian.
 *
 * ### Why the app translates a word the server chose
 *
 * The first rule here was "it is not the app's word to translate", and it was the wrong rule. The
 * status is the loudest thing on a connection card — it sits beside a coloured dot at the top of it
 * — and the exchange writes it in English. A Persian reader looking at their own broker connection
 * read «awaiting provider confirmation», in English, in the one place on the screen that tells them
 * whether their account is working.
 *
 * The reason for the original rule still holds and is kept: only the venue knows *why* it is not
 * connected, and a status this table has never seen must reach the reader rather than be flattened
 * into "خطا". So this is a **closed table with a pass-through**. A word we know is translated; a
 * word we do not know comes out as the server wrote it, with its underscores opened out, exactly as
 * before.
 *
 * ### The shape of the table
 *
 * Keyed on the status lowercased with hyphens folded to underscores, because the two backends and
 * the venues behind them disagree about both. Several wire words map to one Persian word where they
 * mean the same thing to a reader: «connected», «active» and «running» are one state to somebody
 * asking whether their account is working.
 */
object VenueStatusPersian {

    /** Empty for a blank or absent status, which callers read as "say nothing here". */
    fun label(status: String?): String {
        val raw = status?.trim()?.takeIf(String::isNotBlank) ?: return ""
        return TABLE[raw.lowercase().replace('-', '_')] ?: raw.replace('_', ' ')
    }

    private val TABLE: Map<String, String> = mapOf(
        "connected" to "متصل",
        "active" to "متصل",
        "online" to "متصل",
        "running" to "متصل",
        "disconnected" to "قطع",
        "offline" to "قطع",
        "stopped" to "قطع",
        "pending" to "در حال اتصال",
        "connecting" to "در حال اتصال",
        "linking" to "در حال اتصال",
        "reconnecting" to "در حال اتصال دوباره",
        // The exchange has the key and has not yet acknowledged it. Distinct from «در حال اتصال»,
        // which is the app still working: here the app is finished and is waiting on somebody else.
        "awaiting_provider_confirmation" to "در انتظار تأیید صرافی",
        "awaiting_confirmation" to "در انتظار تأیید",
        "pending_approval" to "در انتظار تأیید",
        "error" to "خطا",
        "failed" to "خطا",
        "unauthorized" to "اطلاعات ورود پذیرفته نشد",
        "invalid_credentials" to "اطلاعات ورود پذیرفته نشد",
        "auth_failed" to "اطلاعات ورود پذیرفته نشد",
        "login_failed" to "ورود ناموفق",
        "expired" to "منقضی",
        "revoked" to "باطل شده",
        "rejected" to "رد شد",
        "suspended" to "متوقف",
        "paused" to "متوقف",
        "account_mismatch" to "حساب ناهم‌خوان",
        // Named rather than folded into "خطا": each one names its own fix, and a reader who is told
        // only "error" has nowhere to go.
        "ip_not_whitelisted" to "آی‌پی مجاز نیست",
        "permission_denied" to "دسترسی کلید کافی نیست",
        "rate_limited" to "درخواست بیش از حد",
        "not_configured" to "تنظیم نشده",
        "read_only" to "فقط خواندنی",
    )
}
