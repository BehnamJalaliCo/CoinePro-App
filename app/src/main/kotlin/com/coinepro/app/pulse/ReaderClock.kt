package com.coinepro.app.pulse

import android.content.Context
import android.telephony.TelephonyManager
import java.time.ZoneId

/**
 * The clock the reader actually lives by, for everything the app does on its own (5.29.0).
 *
 * ### Why the phone's own zone is not enough
 *
 * A reader in Iran with the phone set to Los Angeles — common with a US VPN, where a matching clock
 * keeps some services quiet — had the market notifications' night (23:00–08:00 on the phone's
 * clock) land on 09:30–18:30 in Tehran, which is their whole day; the morning summary arrived at
 * 19:30 Tehran time; and the forex road that is open in Iran was not taken first, because that
 * choice keyed on the zone too. The phone's mobile network and SIM say where the phone *is*, and a
 * VPN does not change either, so an Iranian network or SIM puts the reader on Tehran time.
 *
 * Without a SIM or a mobile network — a tablet on Wi-Fi — nothing says otherwise, and the phone's
 * own zone stands.
 */
object ReaderClock {
    val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

    private val IRAN_ZONES = setOf("Asia/Tehran", "Iran")

    /** The zone the quiet hours, the morning summary and the daily brief are measured in. */
    fun zone(context: Context): ZoneId {
        // Asked on every forex request by the mirror's choice, so the network and SIM are read at
        // most once a minute rather than per call.
        val now = System.currentTimeMillis()
        val cached = countriesCache
        val countries = if (cached != null && now - cached.first < COUNTRY_TTL_MILLIS) {
            cached.second
        } else {
            countries(context).also { countriesCache = now to it }
        }
        return pick(ZoneId.systemDefault(), countries)
    }

    @Volatile
    private var countriesCache: Pair<Long, List<String?>>? = null

    /** Whether the reader is in Iran by their clock, their mobile network or their SIM. */
    fun inIran(context: Context): Boolean = zone(context) == TEHRAN

    /** [device], unless it or one of [countries] (ISO codes, any case) says Iran. */
    fun pick(device: ZoneId, countries: List<String?>): ZoneId =
        if (device.id in IRAN_ZONES || countries.any { it.equals(IRAN, ignoreCase = true) }) TEHRAN else device

    private fun countries(context: Context): List<String?> = runCatching {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return@runCatching emptyList()
        listOf(telephony.networkCountryIso, telephony.simCountryIso)
    }.getOrDefault(emptyList())

    private const val IRAN = "ir"
    private const val COUNTRY_TTL_MILLIS = 60_000L
}
