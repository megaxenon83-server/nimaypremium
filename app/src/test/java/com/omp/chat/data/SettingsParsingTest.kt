package com.omp.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pairing payloads come from QR codes, manual typing and deep links. These tests
 * pin the parsing rules, including Norwegian characters and URL-encoded tokens.
 */
class SettingsParsingTest {

    @Test
    fun `pairing scheme becomes http base url`() {
        assertEquals(
            "http://192.168.50.159:7777",
            SettingsStore.normalizeBaseUrl("omp2://192.168.50.159:7777?token=abc123")
        )
        assertEquals(
            "http://100.96.46.66:7777",
            SettingsStore.normalizeBaseUrl("omp://100.96.46.66:7777?token=abc123")
        )
        assertEquals(
            "http://10.0.2.2:7777",
            SettingsStore.normalizeBaseUrl("ompchat://10.0.2.2:7777?token=abc123")
        )
    }

    @Test
    fun `plain urls are kept but normalised`() {
        assertEquals("http://host:7777", SettingsStore.normalizeBaseUrl("  host:7777  "))
        assertEquals("https://host", SettingsStore.normalizeBaseUrl("https://host/v1/"))
        assertEquals("http://host:7777", SettingsStore.normalizeBaseUrl("HTTP://host:7777/?token=x"))
        assertEquals("http://host:7777", SettingsStore.normalizeBaseUrl("http://host:7777#fragment"))
        assertEquals("", SettingsStore.normalizeBaseUrl("   "))
    }

    @Test
    fun `ipv6 and tailscale hosts survive parsing`() {
        assertEquals(
            "http://[fd7a:115c:a1e0::1]:7777",
            SettingsStore.normalizeBaseUrl("omp2://[fd7a:115c:a1e0::1]:7777?token=x")
        )
        assertEquals(
            "http://pc-name.tailnet-1234.ts.net:7777",
            SettingsStore.normalizeBaseUrl("omp2://pc-name.tailnet-1234.ts.net:7777?token=x")
        )
    }

    @Test
    fun `token is extracted and url decoded`() {
        assertEquals("abc123", SettingsStore.tokenFrom("omp2://host:7777?token=abc123"))
        assertEquals("abc123", SettingsStore.tokenFrom("http://host:7777?foo=1&token=abc123&bar=2"))
        assertEquals(
            "æøå test",
            SettingsStore.tokenFrom("omp2://host:7777?token=%C3%A6%C3%B8%C3%A5%20test")
        )
        assertEquals("a+b c", SettingsStore.tokenFrom("omp2://host:7777?token=a%2Bb%20c"))
        assertNull(SettingsStore.tokenFrom("omp2://host:7777"))
        assertNull(SettingsStore.tokenFrom(""))
    }

    @Test
    fun `norwegian room names in a base url do not crash`() {
        val raw = "omp2://stue-pc.local:7777?token=x"
        assertEquals("http://stue-pc.local:7777", SettingsStore.normalizeBaseUrl(raw))
    }
}
