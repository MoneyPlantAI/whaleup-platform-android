package com.whaleup.gameshub.util

import java.net.URI

/** Domain allowlist used for every standalone game WebView navigation. */
object WebViewDomainPolicy {

    fun isAllowed(url: String?, allowedDomains: List<String>?): Boolean {
        val candidate = url?.trim().orEmpty()
        if (candidate.isEmpty() || candidate.equals("about:blank", ignoreCase = true)) {
            return true
        }

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false

        val host = normalizeHost(uri.host) ?: return false
        return allowedDomains.orEmpty().any { entry ->
            val normalizedEntry = normalizeEntry(entry) ?: return@any false
            if (normalizedEntry.isWildcard) {
                host.endsWith(".${normalizedEntry.host}")
            } else {
                host == normalizedEntry.host || host.endsWith(".${normalizedEntry.host}")
            }
        }
    }

    private fun normalizeEntry(entry: String): AllowedHost? {
        val value = entry.trim()
        if (value.isEmpty()) return null

        val hostWithOptionalWildcard = value
            .substringAfter("://", value)
            .substringBefore('/')
            .substringBefore(':')
        val wildcard = hostWithOptionalWildcard?.startsWith("*.") == true
        val host = if (wildcard) hostWithOptionalWildcard?.substring(2) else hostWithOptionalWildcard
        return normalizeHost(host)?.let { AllowedHost(it, wildcard) }
    }

    private fun normalizeHost(host: String?): String? = host
        ?.trim()
        ?.trimEnd('.')
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }

    private data class AllowedHost(val host: String, val isWildcard: Boolean)
}
