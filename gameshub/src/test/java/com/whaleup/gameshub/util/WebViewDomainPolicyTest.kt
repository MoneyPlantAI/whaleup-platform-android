package com.whaleup.gameshub.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewDomainPolicyTest {

    private val domains = listOf(
        "example.com",
        "*.games.test",
        "https://*.dev/",
        "https://cdn.test/path"
    )

    @Test
    fun allowsExactHostsAndSubdomains() {
        assertTrue(WebViewDomainPolicy.isAllowed("https://example.com/game", domains))
        assertTrue(WebViewDomainPolicy.isAllowed("https://play.example.com/game", domains))
        assertTrue(WebViewDomainPolicy.isAllowed("https://cdn.test/image.png", domains))
    }

    @Test
    fun wildcardRequiresASubdomain() {
        assertTrue(WebViewDomainPolicy.isAllowed("https://one.games.test", domains))
        assertTrue(WebViewDomainPolicy.isAllowed("https://ludo.dev/play", domains))
        assertFalse(WebViewDomainPolicy.isAllowed("https://games.test", domains))
        assertFalse(WebViewDomainPolicy.isAllowed("https://dev/play", domains))
    }

    @Test
    fun blocksLookalikeHostsUnsupportedSchemesAndMissingAllowlist() {
        assertFalse(WebViewDomainPolicy.isAllowed("https://example.com.evil.test", domains))
        assertFalse(WebViewDomainPolicy.isAllowed("file:///tmp/game.html", domains))
        assertFalse(WebViewDomainPolicy.isAllowed("javascript:alert(1)", domains))
        assertFalse(WebViewDomainPolicy.isAllowed("https://example.com", emptyList()))
        assertFalse(WebViewDomainPolicy.isAllowed("https://example.com", null))
    }

    @Test
    fun allowsOnlyTheEmptyInitialDocumentWithoutDomains() {
        assertTrue(WebViewDomainPolicy.isAllowed("about:blank", null))
        assertTrue(WebViewDomainPolicy.isAllowed("", null))
    }
}
