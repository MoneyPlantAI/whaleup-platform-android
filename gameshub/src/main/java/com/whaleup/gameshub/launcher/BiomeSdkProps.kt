package com.whaleup.gameshub.launcher

import com.whaleup.gameshub.data.UserConfig
import com.whaleup.gameshub.data.JsMessage
import com.whaleup.gameshub.data.SDKError
import com.whaleup.gameshub.data.SDKEvent

/**
 * Configuration for launching the Biome SDK.
 * Matches Whaleup's WhaleupRnSdkViewProps structure.
 */
data class BiomeSdkProps(
    val userConfig: UserConfig,
    val onMessage: ((JsMessage) -> Unit)? = null,
    val onBiomeError: ((SDKError) -> Unit)? = null,
    val onBiomeEvent: ((SDKEvent) -> Unit)? = null,
    val onPageLoad: ((String) -> Unit)? = null,
    val onPageError: ((String) -> Unit)? = null,
    val closeBiome: (() -> Unit)? = null, // Method to close the biome in host app
    val allowedDomains: List<String>? = null,
    val theme: String = "light",
    val updateTheme: (() -> String)? = null,
    val isImageGenEnabled: Boolean = false
) {
    fun currentTheme(): String = updateTheme?.invoke() ?: theme
}
