package com.coinepro.app

internal fun positiveSignalId(raw: String?): Long? =
    raw?.toLongOrNull()?.takeIf { it > 0L }
