package com.coinepro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkValidationTest {
    @Test
    fun acceptsOnlyPositivePersistedSignalIds() {
        assertEquals(1L, positiveSignalId("1"))
        assertEquals(Long.MAX_VALUE, positiveSignalId(Long.MAX_VALUE.toString()))
        assertNull(positiveSignalId(null))
        assertNull(positiveSignalId(""))
        assertNull(positiveSignalId("0"))
        assertNull(positiveSignalId("-1"))
        assertNull(positiveSignalId("not-a-signal"))
    }
}
