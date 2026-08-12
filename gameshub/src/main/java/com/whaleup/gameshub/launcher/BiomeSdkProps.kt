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
    val onWhaleupSDKError: ((SDKError) -> Unit)? = null,
    val onWhaleupSDKEvent: ((SDKEvent) -> Unit)? = null,
    val onPageLoad: ((String) -> Unit)? = null,
    val onPageError: ((String) -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
    val onCloseSdk: (() -> Unit)? = null,
    val allowedDomains: List<String>? = null
)
