package com.coinepro.core.auth

data class AuthConfig(
    val botUsername: String,
)

data class TelegramAuthPayload(
    val id: Long,
    val firstName: String? = null,
    val lastName: String? = null,
    val username: String? = null,
    val photoUrl: String? = null,
    val authDate: Long,
    val hash: String,
)

data class UserProfile(
    val telegramId: Long,
    val name: String,
    val username: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val kycStatus: String = "none",
    val isVip: Boolean = false,
    val isPaid: Boolean = false,
    val panelApproved: Boolean = false,
    val panelAllowed: Boolean = false,
    val panelState: String = "buy",
    val plan: String = "free",
    /**
     * The plan's name as the server would say it to this reader, when it sent one.
     *
     * Separate from [plan], which is the identifier the app compares against and must not change
     * with language. Null wherever the server named no such thing — the card then falls back to
     * [plan], and never to a translation the app invented for a plan it did not define.
     */
    val planLabel: String? = null,
    val planExpiresAt: String? = null,
    val disclaimerAccepted: Boolean = false,
)

data class AuthSession(
    val token: String,
    val profile: UserProfile,
)

data class EntitlementSnapshot(
    val isVip: Boolean,
    val isPaid: Boolean,
    val panelAllowed: Boolean,
    val panelState: String,
    val plan: String,
    /** See [UserProfile.planLabel]. */
    val planLabel: String?,
    val expiresAt: String?,
) {
    val hasPaidPanelAccess: Boolean get() = isPaid && panelAllowed
}

fun UserProfile.toEntitlementSnapshot() = EntitlementSnapshot(
    isVip = isVip,
    isPaid = isPaid,
    panelAllowed = panelAllowed,
    panelState = panelState,
    plan = plan,
    planLabel = planLabel,
    expiresAt = planExpiresAt,
)
