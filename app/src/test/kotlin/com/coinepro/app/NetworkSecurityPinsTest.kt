package com.coinepro.app

import com.coinepro.core.network.NetworkFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The platform pin-set and OkHttp's pin list are the same list.
 *
 * Two copies of nine digests would drift the first time one of them was rotated; this reads the
 * XML the manifest points at and the build's own `BuildConfig.CERTIFICATE_PINS` and holds them
 * equal, host by host, with the same expiry.
 */
class NetworkSecurityPinsTest {

    @Test
    fun `the network security config pins exactly what OkHttp pins, with the same expiry`() {
        val file = listOf("src/main/res/xml/network_security_config.xml", "app/src/main/res/xml/network_security_config.xml")
            .map(::File).first { it.exists() }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val configs = document.getElementsByTagName("domain-config")
        val fromXml = mutableMapOf<String, Set<String>>()
        val expirations = mutableSetOf<String>()
        for (index in 0 until configs.length) {
            val config = configs.item(index) as org.w3c.dom.Element
            val host = config.getElementsByTagName("domain").item(0).textContent.trim()
            val pins = config.getElementsByTagName("pin")
            fromXml[host] = (0 until pins.length).map { "sha256/" + pins.item(it).textContent.trim() }.toSet()
            expirations += (config.getElementsByTagName("pin-set").item(0) as org.w3c.dom.Element).getAttribute("expiration")
        }
        val fromBuild = NetworkFactory.parsePins(BuildConfig.CERTIFICATE_PINS).mapValues { it.value.toSet() }
        assertEquals(fromBuild, fromXml)
        assertEquals(setOf(java.time.Instant.ofEpochMilli(BuildConfig.CERTIFICATE_PINS_UNTIL).toString().substring(0, 10)), expirations)
        assertTrue(fromXml.values.all { it.size >= 2 })
    }
}
