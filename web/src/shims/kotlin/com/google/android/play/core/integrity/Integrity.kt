@file:Suppress("unused", "UNUSED_PARAMETER")

package com.google.android.play.core.integrity

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

/** Play Integrity attests an installed Android app; a page is not one, and is never attested. */
interface IntegrityManager {
    fun requestIntegrityToken(request: IntegrityTokenRequest): Task<IntegrityTokenResponse>
}

abstract class IntegrityTokenResponse { abstract fun token(): String }

class IntegrityTokenRequest private constructor() {
    class Builder {
        fun setNonce(nonce: String): Builder = this
        fun setCloudProjectNumber(number: Long): Builder = this
        fun build(): IntegrityTokenRequest = IntegrityTokenRequest()
    }
    companion object { fun builder(): Builder = Builder() }
}

object IntegrityManagerFactory {
    fun create(context: android.content.Context): IntegrityManager = object : IntegrityManager {
        override fun requestIntegrityToken(request: IntegrityTokenRequest): Task<IntegrityTokenResponse> =
            Tasks.forException(IllegalStateException("Play Integrity is not available in a browser"))
    }
}
