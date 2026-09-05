package com.coinepro.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The level-one body keeps the server's original contract for an Iranian card and widens it for
 * everyone else.
 *
 * Two shapes, one class, and the shape is decided by the identity rather than by a flag somebody
 * has to remember to set. A server that predates the generic fields keeps receiving exactly what
 * it always did; a server that has learned them receives them only when they apply.
 */
class KycLevel1RequestTest {

    private fun identity(
        country: String = KycIdentity.IRAN,
        type: KycDocumentType = KycDocumentType.NATIONAL_ID,
        number: String = "۰۰۱۲۳۴۵۶۷۸",
    ) = KycIdentity(
        fullName = " بهنام جلالی ",
        country = country,
        documentType = type,
        documentNumber = number,
        birthDate = "۱۳۷۰/۰۵/۱۲",
        phone = "۰۹۱۲۱۲۳۴۵۶۷",
    )

    @Test
    fun `an Iranian national card travels as national_id and nothing else about the document`() {
        val request = KycLevel1Request.of(identity())

        assertEquals("0012345678", request.nationalId)
        assertNull(request.country)
        assertNull(request.documentType)
        assertNull(request.documentNumber)
        // The rest of the body is unchanged from the contract that shipped: trimmed name, Latin
        // digits in the date and the phone, the calendar left alone.
        assertEquals("بهنام جلالی", request.fullName)
        assertEquals("1370/05/12", request.birthDate)
        assertEquals("09121234567", request.phone)
    }

    @Test
    fun `any other identity travels as country, document type and number`() {
        val request = KycLevel1Request.of(identity(country = "de", type = KycDocumentType.PASSPORT, number = "c01x00t47"))

        assertNull(request.nationalId)
        assertEquals("DE", request.country)
        assertEquals("passport", request.documentType)
        assertEquals("c01x00t47", request.documentNumber)
    }

    @Test
    fun `an Iranian passport is not a national card`() {
        // The country alone does not pick the old contract; the document does. A reader in Iran
        // verifying with a passport has no `national_id` to send.
        val request = KycLevel1Request.of(identity(type = KycDocumentType.PASSPORT, number = "A12345678"))

        assertNull(request.nationalId)
        assertEquals("IR", request.country)
        assertEquals("passport", request.documentType)
    }

    @Test
    fun `the wire spelling of a document type is fixed, not the enum name`() {
        assertEquals("driver_licence", KycDocumentType.DRIVER_LICENCE.wire)
        assertEquals(KycDocumentType.DRIVER_LICENCE, KycDocumentType.fromWire(" Driver_Licence "))
        assertNull(KycDocumentType.fromWire("id"))
    }
}
