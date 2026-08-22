package com.coinepro.core.auth

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SessionMemory {
    private val tokenRef = AtomicReference<String?>(null)
    private val unauthorizedMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val unauthorized = unauthorizedMutable.asSharedFlow()

    fun token(): String? = tokenRef.get()

    fun setToken(token: String?) {
        tokenRef.set(token)
    }

    fun notifyUnauthorized() {
        unauthorizedMutable.tryEmit(Unit)
    }
}
