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
    expiresAt = planExpiresAt,
)
