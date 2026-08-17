package com.whaleup.gameshub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLoaderPolicyTest {

    @Test
    fun normalizesWrappedHttpUrls() {
        assertEquals("https://example.com/image.png", normalizeImageUrl(" [\"https://example.com/image.png\"] "))
        assertEquals("https://example.com/image.png", normalizeImageUrl("'https://example.com/image.png'"))
        assertNull(normalizeImageUrl("file:///tmp/image.png"))
        assertNull(normalizeImageUrl("not a URL"))
    }

    @Test
    fun addsJsDelivrFallbackBeforeRawGitHubUrl() {
        val rawUrl = "https://raw.githubusercontent.com/owner/repository/main/images/card.png"

        assertEquals(
            listOf(
                "https://cdn.jsdelivr.net/gh/owner/repository@main/images/card.png",
                rawUrl
            ),
            imageUrlCandidates(rawUrl)
        )
    }

    @Test
    fun retriesOnlyTransientResponses() {
        assertTrue(isRetryableImageResponse(-1))
        assertTrue(isRetryableImageResponse(429))
        assertTrue(isRetryableImageResponse(503))
        assertFalse(isRetryableImageResponse(404))
    }
}
