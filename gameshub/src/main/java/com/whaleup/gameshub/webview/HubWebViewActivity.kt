package com.whaleup.gameshub.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.Manifest
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.BiomeMessageType
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.data.CatalogCache
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.data.PlayerPrefsManager
import com.whaleup.gameshub.data.isMultiplayerGame
import com.whaleup.gameshub.data.SDKError
import com.whaleup.gameshub.data.SDKEvent
import com.whaleup.gameshub.data.UserConfig
import com.whaleup.gameshub.messaging.BiomeSdkError
import com.whaleup.gameshub.messaging.BiomeSdkEvent
import com.whaleup.gameshub.messaging.RouteAction
import com.whaleup.gameshub.messaging.SharePayload
import com.whaleup.gameshub.messaging.toMap
import com.whaleup.gameshub.messaging.toList
import com.whaleup.gameshub.multiplayer.MultiplayerModule
import com.whaleup.gameshub.network.APIBridge
import com.whaleup.gameshub.network.APICallback
import com.whaleup.gameshub.network.HubEndpoint
import com.whaleup.gameshub.util.InternetErrorRetryHandler
import com.whaleup.gameshub.util.SdkErrorPresenter
import com.whaleup.gameshub.util.WebViewDomainPolicy
import androidx.core.content.FileProvider
import org.json.JSONObject
import org.json.JSONArray

private const val TAG = "HubWebViewActivity"

/**
 * Activity hosting the WebView for games.
 * Implements ActionProcessor to handle all RouteActions from the MessageRouter.
 *
 * This is the Kotlin equivalent of WhaleupRnSdkView.tsx's processActions + handlers.
 */
class HubWebViewActivity : AppCompatActivity(), ActionProcessor, InternetErrorRetryHandler {

    companion object {
        const val EXTRA_REWARD_GAME_NAME = "com.whaleup.gameshub.extra.REWARD_GAME_NAME"
        const val EXTRA_REWARD_COINS = "com.whaleup.gameshub.extra.REWARD_COINS"
    }

    private lateinit var webView: WebView
    private lateinit var bridge: WhaleBridge
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkInterruptionActive = false
    private val navigationBarHideHandler = Handler(Looper.getMainLooper())
    private val navigationBarHideRunnable = Runnable { hideBottomNavigationBar() }
    private var entryUrl: String? = null
    private var multiplayerModule: MultiplayerModule? = null
    private var domainErrorDialog: AlertDialog? = null

    // Microphone permission request handling
    private var pendingPermissionRequest: PermissionRequest? = null
    private val audioPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            if (request != null) {
                if (isGranted) {
                    Log.d(TAG, "Audio permission granted by user, granting WebView request")
                    request.grant(request.resources)
                } else {
                    Log.w(TAG, "Audio permission denied by user, denying WebView request")
                    request.deny()
                }
            }
        }

    // WebView file chooser callback handling
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null
            if (callback != null) {
                var results: Array<Uri>? = null
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                }
                callback.onReceiveValue(results)
            }
        }

    // API de-duplication (matches Whaleup's apiInFlightRequests)
    private val apiInFlightRequests = mutableMapOf<String, Boolean>()

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.whaleup.gameshub.R.style.Theme_GamesHub_Light)
        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#00A8F3")))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub_webview)
        GamesHubSession.initialize(this)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        hideBottomNavigationBar()

        val toolbar = findViewById<Toolbar>(R.id.toolbarWebView)
        webView = findViewById(R.id.webView)
        webView.setBackgroundColor(Color.parseColor("#00A8F3"))

        val webViewContainer = findViewById<View>(R.id.webViewContainer)
        webViewContainer.setBackgroundColor(Color.parseColor("#00A8F3"))
        ViewCompat.setOnApplyWindowInsetsListener(webViewContainer) { v, insets ->
            val isNavigationBarVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                        WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(top = 0, bottom = systemBars.bottom)
            if (isNavigationBarVisible && !isImeVisible) {
                scheduleBottomNavigationBarHide()
            } else if (!isNavigationBarVisible) {
                navigationBarHideHandler.removeCallbacks(navigationBarHideRunnable)
            }
            insets
        }

        val entryUrl = intent.getStringExtra("ENTRY_URL") ?: return finish()
        this.entryUrl = entryUrl
        val gameName = intent.getStringExtra("GAME_NAME") ?: "Game"
        toolbar.title = gameName
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        // Initialize bridge with this activity as the action processor
        bridge = WhaleBridge(this, this)
        bridge.attachWebView(webView)
        webView.addJavascriptInterface(bridge, "WhaleBridge")
        webView.addJavascriptInterface(bridge, "AndroidBridge")

        val config = reapplySessionConfig()

        // Set up API error handler
        APIBridge.onError = { error ->
            // Silence transient errors (429, 503)
            if (error.message.contains("429") || error.message.contains("503")) {
                Log.d(TAG, "Silencing transient API error for: ${error.endpoint}")
            } else {
                Log.e(TAG, "API error: $error")
                reportSdkError(
                    SDKError(
                        type = BiomeMessageType.LOAD_FAILURE,
                        action = BiomeMessageAction.INTERNAL_ERROR,
                        data = mapOf(
                            "endpoint" to error.endpoint,
                            "method" to error.method,
                            "message" to error.message
                        )
                    )
                )
            }
        }

        // WebView client with context injection and page load callbacks
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = shouldBlockNavigation(request?.url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                shouldBlockNavigation(url?.let(Uri::parse))

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectContext(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectContext(view)
                url?.let { GamesHubSession.props?.onPageLoad?.invoke(it) }

                // Restore state after page load (like Whaleup's restoreStateAfterReload)
                sendProfileToWebView()
                BiomeState.getCurrentGameId()?.let { sendGameConfigToWebView(it) }
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                val message = description ?: "Unknown error"
                GamesHubSession.props?.onPageError?.invoke(message)
                reportSdkError(
                    SDKError(
                        type = BiomeMessageType.LOAD_FAILURE,
                        action = BiomeMessageAction.GAME_LOAD_ERROR,
                        data = mapOf(
                            "code" to errorCode,
                            "message" to message,
                            "url" to failingUrl
                        )
                    )
                )
            }
        }

        // Enable console logs, audio permission handling, and file chooser in WebView
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    val message = "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}"
                    when (it.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.e("WebConsole", message)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w("WebConsole", message)
                        ConsoleMessage.MessageLevel.LOG -> Log.i("WebConsole", message)
                        ConsoleMessage.MessageLevel.TIP -> Log.d("WebConsole", message)
                        else -> Log.v("WebConsole", message)
                    }
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val requestedResources = request.resources ?: arrayOf()
                Log.d(TAG, "onPermissionRequest called for resources: ${requestedResources.joinToString()}")

                if (requestedResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    if (ContextCompat.checkSelfPermission(
                            this@HubWebViewActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d(TAG, "RECORD_AUDIO permission already granted, granting web request")
                        request.grant(requestedResources)
                    } else {
                        Log.d(TAG, "Requesting RECORD_AUDIO runtime permission for web audio capture")
                        pendingPermissionRequest?.deny()
                        pendingPermissionRequest = request
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    request.grant(requestedResources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                Log.d(TAG, "onShowFileChooser called")
                this@HubWebViewActivity.filePathCallback?.onReceiveValue(null)
                this@HubWebViewActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }

                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch file chooser intent", e)
                    this@HubWebViewActivity.filePathCallback?.onReceiveValue(null)
                    this@HubWebViewActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        // Register network connectivity listener
        registerNetworkListener()

        // Hydrate cached state and load URL
        BiomeState.hydrateUserProfile(config?.userId)

        if (!SdkErrorPresenter.isInternetAvailable(this)) {
            reportSdkError(
                SDKError(
                    type = BiomeMessageType.NETWORK_INTERRUPTION,
                    action = BiomeMessageAction.NETWORK_INTERRUPTED,
                    data = mapOf("reason" to "No internet connection", "retryable" to true)
                )
            )
            return
        }

        if (WebViewDomainPolicy.isAllowed(entryUrl, resolvedAllowedDomains())) {
            webView.loadUrl(entryUrl)
        } else {
            notifyBlockedNavigation(entryUrl)
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        val config = reapplySessionConfig()
        if (BiomeState.getUserProfile() == null) {
            BiomeState.hydrateUserProfile(config?.userId)
        }
        webView.onResume()
        webView.resumeTimers()
        hideBottomNavigationBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        navigationBarHideHandler.removeCallbacks(navigationBarHideRunnable)
        unregisterNetworkListener()
        multiplayerModule?.disconnect()
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        webView.destroy()
    }

    private fun scheduleBottomNavigationBarHide() {
        navigationBarHideHandler.removeCallbacks(navigationBarHideRunnable)
        navigationBarHideHandler.postDelayed(navigationBarHideRunnable, 3_000L)
    }

    private fun hideBottomNavigationBar() {
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.navigationBars())
    }

    private fun reapplySessionConfig(): UserConfig? {
        val config = GamesHubSession.props?.userConfig?.takeIf { it.userId.isNotBlank() }
            ?: BiomeState.getUserConfig()
        if (config == null) {
            Log.w(TAG, "No userConfig available to reapply")
            return null
        }

        configureSdk(config)
        return config
    }

    private fun resolvedAllowedDomains(): List<String>? =
        GamesHubSession.props?.userConfig?.allowedDomains
            ?: BiomeState.getUserConfig()?.allowedDomains

    private fun shouldBlockNavigation(uri: Uri?): Boolean {
        val url = uri?.toString() ?: return false
        if (WebViewDomainPolicy.isAllowed(url, resolvedAllowedDomains())) return false

        notifyBlockedNavigation(url)
        return true
    }

    private fun notifyBlockedNavigation(url: String) {
        val message = "Domain not allowed: $url. Add this domain to userConfig.allowedDomains."
        Log.w(TAG, message)
        GamesHubSession.props?.onPageError?.invoke(message)
        GamesHubSession.props?.onWhaleupSDKError?.invoke(
            SDKError(
                type = BiomeMessageType.NAVIGATION_ERROR,
                action = BiomeMessageAction.NAVIGATION_BLOCKED,
                data = mapOf("url" to url, "reason" to message, "retryable" to false)
            )
        )

        if (isFinishing || isDestroyed || domainErrorDialog?.isShowing == true) return
        domainErrorDialog = AlertDialog.Builder(this)
            .setTitle("Domain Not Allowed")
            .setMessage(message)
            .setPositiveButton("BACK TO GAMES") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun configureSdk(config: UserConfig) {
        if (config.userId.isBlank()) {
            Log.w(TAG, "Skipping SDK config with blank userId")
            return
        }

        APIBridge.baseUrl = config.apiBaseUrl
        APIBridge.authToken = config.authToken
        APIBridge.userAgent = config.userAgent
        APIBridge.timezone = config.timezone
        BiomeState.setUserConfig(config)
        APIBridge.setSessionId(
            BiomeState.getSessionId().takeIf { it.isUsableSessionId() }
                ?: config.sessionId.takeIf { it.isUsableSessionId() }
        )
        PlayerPrefsManager.setUserId(config.userId)
    }

    private fun String?.isUsableSessionId(): Boolean =
        !isNullOrBlank() && this != "sessionId"

    override fun retryAfterInternetError() {
        if (!SdkErrorPresenter.isInternetAvailable(this)) {
            reportSdkError(
                SDKError(
                    type = BiomeMessageType.NETWORK_INTERRUPTION,
                    action = BiomeMessageAction.NETWORK_INTERRUPTED,
                    data = mapOf("reason" to "No internet connection", "retryable" to true)
                )
            )
            return
        }

        SdkErrorPresenter.dismissInternetErrorDialog()
        val url = webView.url ?: entryUrl
        if (url != null) {
            webView.loadUrl(url)
        } else {
            webView.reload()
        }
    }

    // region ActionProcessor Implementation

    override fun processActions(actions: List<RouteAction>) {
        actions.forEach { action ->
            Log.d(TAG, "Processing action: ${action.javaClass.simpleName}")
            when (action) {
                is RouteAction.Bubble -> {
                    val msg = action.message
                    GamesHubSession.props?.onMessage?.invoke(
                        com.whaleup.gameshub.data.JsMessage(msg.type, msg.action, msg.data)
                    )
                }

                is RouteAction.SdkError -> {
                    notifySdkError(action.error)
                }

                is RouteAction.SdkEvent -> {
                    notifySdkEvent(action.event)
                    // Handle special exitGame event
                    if (action.event.type == "exitGame" && action.event.action == "beginGameExit") {
                        Log.d(TAG, "Game exit requested, returning to hub")
                    }
                }

                is RouteAction.LoadHub -> {
                    // Return to hub activity
                    finish()
                }

                is RouteAction.LoadGame -> {
                    BiomeState.setCurrentGameId(action.gameId)
                    loadGame(action.gameId)
                }

                is RouteAction.Close -> {
                    GamesHubSession.props?.onClose?.invoke()
                    finish()
                }

                is RouteAction.CloseSdk -> {
                    (GamesHubSession.props?.onCloseSdk ?: GamesHubSession.props?.onClose)?.invoke()
                    finish()
                }

                is RouteAction.SendProfile -> {
                    sendProfileToWebView()
                }

                is RouteAction.SendGameConfig -> {
                    sendGameConfigToWebView(action.gameId)
                }

                is RouteAction.ApiCall -> {
                    handleApiCall(action)
                }

                is RouteAction.HostDecision -> {
                    handleHostDecision(action)
                }

                is RouteAction.GetPlayerPref -> {
                    handleGetPlayerPref(action.key, action.defaultValue)
                }

                is RouteAction.SetPlayerPref -> {
                    handleSetPlayerPref(action.key, action.value)
                }

                is RouteAction.DeletePlayerPref -> {
                    PlayerPrefsManager.delete(action.key)
                }

                is RouteAction.MigratePlayerPrefs -> {
                    PlayerPrefsManager.migrateFromLocalStorage(action.prefs)
                }

                is RouteAction.UserLogout -> {
                    GamesHubSession.logout()
                    finish()
                }

                is RouteAction.ShareIntent -> {
                    handleShare(action.text, action.payload)
                }

                is RouteAction.CopyToClipboard -> {
                    handleCopyToClipboard(action.text)
                }

                is RouteAction.Ignore -> { /* no-op */ }

                is RouteAction.MultiplayerCommand -> {
                    if (multiplayerModule == null) {
                        val gameId = intent.getStringExtra("GAME_ID") ?: BiomeState.getCurrentGameId()
                        val game = gameId?.let(CatalogCache::findById)
                        val gameEngineUrl = game?.gameEngineUrl
                        val currentConfig = BiomeState.getUserConfig()
                        val playerId = currentConfig?.userId?.trim()
                        when {
                            gameId.isNullOrEmpty() -> sendMultiplayerSetupError("Missing current game ID", gameId)
                            game == null -> sendMultiplayerSetupError("Game '$gameId' is missing from the catalog", gameId)
                            gameEngineUrl.isNullOrBlank() -> sendMultiplayerSetupError(
                                "Multiplayer engine URL is not configured for game '$gameId'.",
                                gameId
                            )
                            playerId.isNullOrEmpty() -> sendMultiplayerSetupError("Missing multiplayer player ID", gameId)
                            else -> {
                                multiplayerModule = MultiplayerModule(
                                    bridge = bridge,
                                    engineUrl = gameEngineUrl,
                                    playerId = playerId,
                                    currentGameId = { BiomeState.getCurrentGameId() },
                                    tokenProvider = {
                                        APIBridge.getMultiplayerTokenSuspend(
                                            playerId,
                                            gameEngineUrl
                                        )
                                    },
                                    onSocketReconnected = {
                                        runOnUiThread { notifyNetworkRestoredIfNeeded() }
                                    },
                                    onReconnectExpired = { timeoutMs ->
                                        runOnUiThread { handleMultiplayerReconnectExpired(timeoutMs) }
                                    }
                                )
                                Log.i(TAG, "MultiplayerModule enabled for game='$gameId', engine='$gameEngineUrl', action='${action.action}'")
                            }
                        }
                    }
                    multiplayerModule?.handleCommand(action.action, action.data)
                }
            }
        }
    }

    // endregion

    // region Profile & Config

    private fun sendProfileToWebView(source: String? = null) {
        val userProfile = BiomeState.getUserProfile()
        val currentUserConfig = BiomeState.getUserConfig()
        val profileSource = source ?: BiomeState.getProfileSource() ?: "cache"

        if (userProfile != null) {
            val profileMap = BiomeState.getProfileAsMap(userProfile)
            val envelope = mapOf(
                "source" to profileSource,
                "timestamp" to (BiomeState.getProfileTimestamp() ?: System.currentTimeMillis()),
                "gameId" to (BiomeState.getCurrentGameId() ?: "hub"),
                "profile" to profileMap
            )
            bridge.sendMessageToWebView(
                BiomeMessageType.STATE_SYNC,
                BiomeMessageAction.UPDATE_PROFILE,
                envelope
            )
            Log.i(TAG, "Sent profile with source=$profileSource")
        } else if (currentUserConfig != null) {
            val defaultUser = BiomeState.getDefaultUser(currentUserConfig)
            val profileMap = BiomeState.getProfileAsMap(defaultUser)
            val envelope = mapOf(
                "source" to "cache", // Default profile is always untrusted
                "timestamp" to System.currentTimeMillis(),
                "gameId" to (BiomeState.getCurrentGameId() ?: "hub"),
                "profile" to profileMap
            )
            bridge.sendMessageToWebView(
                BiomeMessageType.STATE_SYNC,
                BiomeMessageAction.UPDATE_PROFILE,
                envelope
            )
            Log.i(TAG, "Sent default profile with source=cache")
        } else {
            Log.w(TAG, "No userConfig or userProfile available in BiomeState")
            reportSdkError(
                SDKError(
                    type = BiomeMessageType.CRITICAL_FAILURE,
                    action = "sendProfile",
                    data = mapOf("reason" to "No user profile or config available")
                )
            )
        }
    }

    private fun sendGameConfigToWebView(gameId: String) {
        val game = com.whaleup.gameshub.data.CatalogCache.findById(gameId) ?: return
        bridge.sendMessageToWebView(
            BiomeMessageType.STATE_SYNC,
            BiomeMessageAction.UPDATE_GAME_CONFIG,
            mapOf("gameId" to game.id, "gameConfig" to game.gameConfig)
        )
    }

    private fun loadGame(gameId: String?) {
        if (gameId.isNullOrEmpty()) {
            Log.w(TAG, "loadGame called without gameId")
            return
        }
        BiomeState.setCurrentGameId(gameId)
        val game = com.whaleup.gameshub.data.CatalogCache.findById(gameId) ?: return

        val intent = Intent(this, HubWebViewActivity::class.java)
        intent.putExtra("ENTRY_URL", game.entryUrl)
        intent.putExtra("GAME_ID", game.id)
        intent.putExtra("GAME_NAME", game.name)
        startActivity(intent)
    }

    // endregion

    // region API Handling

    private fun handleApiCall(action: RouteAction.ApiCall) {
        val requestKey = "${action.endpoint}:${action.method}:${action.route}:${action.customEndpoint}:${action.data}:${action.respondWith ?: "no-response"}"

        if (apiInFlightRequests.containsKey(requestKey)) {
            Log.d(TAG, "Deduping API request: $requestKey")
            return
        }
        apiInFlightRequests[requestKey] = true

        if (action.endpoint == BiomeMessageAction.CUSTOM_REQUEST) {
            val method = action.method
            val route = action.route
            if (method.isNullOrBlank() || route.isNullOrBlank()) {
                Log.w(TAG, "Custom request missing method or route")
                apiInFlightRequests.remove(requestKey)
                return
            }
            APIBridge.customCompositeRequest(
                method = method,
                route = route,
                data = action.data,
                endpoint = action.customEndpoint,
                callback = customApiCallback(action, requestKey)
            )
            return
        }

        val endpoint = HubEndpoint.fromName(action.endpoint)
        if (endpoint == null) {
            Log.w(TAG, "Unknown API endpoint: ${action.endpoint}")
            apiInFlightRequests.remove(requestKey)
            return
        }

        val callback = object : APICallback {
            override fun onSuccess(response: String) {
                apiInFlightRequests.remove(requestKey)
                try {
                    val responseData = JSONObject(response)

                    handleApiResponse(endpoint, responseData)

                    // Send response back to WebView if respondWith is specified
                    if (action.respondWith != null) {
                        if (action.endpoint == HubEndpoint.GET_USER_PROFILE.name) {
                            Log.i(TAG, "Server profile sent via envelope: ${action.respondWith}")
                        } else {
                            val responseMap = responseData.toMap()
                            bridge.sendMessageToWebView(
                                BiomeMessageType.STATE_SYNC,
                                action.respondWith,
                                responseMap
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process API response", e)
                }
            }

            override fun onError(code: Int, message: String) {
                apiInFlightRequests.remove(requestKey)
                Log.e(TAG, "API call failed: ${action.endpoint} - $message")

                // Notify WebView of failure
                bridge.sendMessageToWebView(
                    BiomeMessageType.CRITICAL_FAILURE,
                    BiomeMessageAction.CRITICAL_FAILURE,
                    mapOf(
                        "reason" to message,
                        "retryable" to false,
                        "endpoint" to action.endpoint,
                        "requestData" to action.data
                    )
                )

                reportSdkError(
                    SDKError(
                        type = BiomeMessageType.CRITICAL_FAILURE,
                        action = BiomeMessageAction.INTERNAL_ERROR,
                        data = mapOf(
                            "reason" to message,
                            "endpoint" to action.endpoint
                        )
                    )
                )
            }
        }

        val requestData = action.data?.toMutableMap() ?: mutableMapOf()
        val requestUserId = requestData["userId"] as? String
        if (requestUserId.isNullOrBlank()) {
            val fallbackUserId = BiomeState.getUserConfig()?.userId?.takeIf { it.isNotBlank() }
                ?: GamesHubSession.props?.userConfig?.userId?.takeIf { it.isNotBlank() }
            if (fallbackUserId != null) {
                requestData["userId"] = fallbackUserId
            } else {
                requestData.remove("userId")
            }
        }

        when (endpoint) {
            HubEndpoint.GET_USER_PROFILE -> {
                // Send cached profile first
                sendProfileToWebView("cache")
                APIBridge.getUserProfile(requestData, callback)
            }
            HubEndpoint.GAME_STARTED -> APIBridge.gameStarted(
                requestData, currentGameModeType(), callback
            )
            HubEndpoint.GAME_ENDED -> APIBridge.gameEnded(
                requestData, currentGameModeType(), callback
            )
            HubEndpoint.CLAIM_GULLAK -> APIBridge.claimGullak(
                requestData["userId"] as? String ?: "",
                callback
            )
            HubEndpoint.GET_GULLAK -> APIBridge.getGullak(
                requestData["userId"] as? String ?: "",
                callback
            )
            HubEndpoint.VIDEO_WATCHED -> APIBridge.videoWatched(
                requestData["userId"] as? String ?: "",
                callback
            )
            HubEndpoint.LOG_CLIENT_ERROR -> APIBridge.logClientError(requestData, callback)
            HubEndpoint.GET_STRINGS -> APIBridge.getStrings(callback)
            HubEndpoint.GET_CONFIG -> APIBridge.getConfig(callback)
            HubEndpoint.CATALOG -> APIBridge.get(endpoint, callback)
            HubEndpoint.GET_LEADERBOARD -> APIBridge.getLeaderboard(requestData, callback)
        }
    }

    private fun customApiCallback(
        action: RouteAction.ApiCall,
        requestKey: String
    ): APICallback = object : APICallback {
        override fun onSuccess(response: String) {
            apiInFlightRequests.remove(requestKey)
            val responseData = runCatching {
                when (val value = org.json.JSONTokener(response).nextValue()) {
                    is JSONObject -> value.toMap()
                    is org.json.JSONArray -> value.toList()
                    JSONObject.NULL -> null
                    else -> value
                }
            }.getOrElse { response }
            action.respondWith?.let {
                bridge.sendMessageToWebView(BiomeMessageType.STATE_SYNC, it, responseData)
            }
        }

        override fun onError(code: Int, message: String) {
            apiInFlightRequests.remove(requestKey)
            bridge.sendMessageToWebView(
                BiomeMessageType.CRITICAL_FAILURE,
                BiomeMessageAction.CRITICAL_FAILURE,
                mapOf(
                    "reason" to message,
                    "retryable" to false,
                    "endpoint" to (action.customEndpoint ?: APIBridge.compositeEndpoint),
                    "route" to action.route,
                    "method" to action.method
                )
            )
        }
    }

    private fun currentGameModeType(): String {
        val gameId = BiomeState.getCurrentGameId()
        val game = gameId?.let {
            com.whaleup.gameshub.data.CatalogCache.findById(it)
        }
        return if (game?.isMultiplayerGame() == true) "MP" else "SP"
    }

    /**
     * Post-process API responses to update local state (mirrors Whaleup's handleApiCall).
     */
    private fun handleApiResponse(endpoint: HubEndpoint, response: JSONObject) {
        when (endpoint) {
            HubEndpoint.GET_USER_PROFILE -> {
                val profileMap = response.toMap()
                BiomeState.setUserProfile(profileMap, "server")
                sendProfileToWebView("server")
            }
            HubEndpoint.GAME_STARTED -> {
                val gameSessionId = response.optString("gameSessionId").trim()
                if (gameSessionId.isNotEmpty()) {
                    BiomeState.setGameSessionId(gameSessionId)
                    Log.d(TAG, "Stored game-start session for the game-end payload")
                } else {
                    Log.e(TAG, "game-start response is missing gameSessionId")
                }
            }
            HubEndpoint.GAME_ENDED -> {
                BiomeState.setGameSessionId(null)
                val coinsEarned = response.optInt("coinsEarnedForGame", -1)
                val gemsEarned = response.optInt("gemsEarned", -1)
                if (coinsEarned >= 0) BiomeState.incrementCoinsEarned(coinsEarned)
                if (gemsEarned >= 0) BiomeState.incrementGemsEarned(gemsEarned)
                if (coinsEarned > 0) {
                    setResult(
                        Activity.RESULT_OK,
                        Intent().apply {
                            putExtra(
                                EXTRA_REWARD_GAME_NAME,
                                this@HubWebViewActivity.intent.getStringExtra("GAME_NAME") ?: "Game"
                            )
                            putExtra(EXTRA_REWARD_COINS, coinsEarned)
                        }
                    )
                }
            }
            HubEndpoint.GET_GULLAK, HubEndpoint.CLAIM_GULLAK -> {
                BiomeState.updateGullakProfile(response.toMap())
            }
            HubEndpoint.VIDEO_WATCHED,
            HubEndpoint.LOG_CLIENT_ERROR,
            HubEndpoint.GET_STRINGS -> Unit
            else -> { /* no post-processing needed */ }
        }
    }

    // endregion

    // region Host Decision / Recovery

    private fun handleHostDecision(action: RouteAction.HostDecision) {
        when (action.strategy) {
            "exit", "exitExperience" -> {
                GamesHubSession.props?.onClose?.invoke()
                finish()
            }
            "returnToHub" -> finish()
            "reload", "retry", "refreshWebView" -> {
                val target = (action.data?.get("target") as? String)
                val gameId = (action.data?.get("gameId") as? String)
                if (target == "game" && !gameId.isNullOrEmpty()) {
                    loadGame(gameId)
                } else {
                    // Reload current page
                    webView.reload()
                }
            }
            else -> webView.reload()
        }
    }

    // endregion

    // region Player Prefs

    private fun handleGetPlayerPref(key: String, defaultValue: Any?) {
        val value = PlayerPrefsManager.get(key, defaultValue)
        bridge.sendMessageToWebView(
            BiomeMessageType.PLAYER_PREFS,
            BiomeMessageAction.PLAYER_PREF_VALUE,
            mapOf("key" to key, "value" to value)
        )
    }

    private fun handleSetPlayerPref(key: String, value: Any?) {
        // Sync FTUE state (matching Whaleup behavior)
        if (key == "ftue_completed" || key == "ftue_reward_given") {
            val boolValue = value == true || value == "true" || value == 1
            BiomeState.setFtueRewardGiven(boolValue)
        }
        PlayerPrefsManager.set(key, value)
    }

    // endregion

    // region Share & Clipboard

    private fun handleShare(text: String, payload: SharePayload) {
    try {
        if (payload.image.isNullOrEmpty()) {
            // Text-only fallback
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                payload.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            }
            startActivity(Intent.createChooser(shareIntent, payload.title ?: "Share"))
            bridge.sendMessageToWebView(
                BiomeMessageType.SHARE,
                BiomeMessageAction.SHARE_SUCCESS,
                mapOf("success" to true)
            )
            return
        }

        // Decode Base64 image
        val rawBase64 = if (payload.image.contains(",")) {
            payload.image.substringAfter(",")
        } else {
            payload.image
        }
        val cleanBase64 = rawBase64.replace(Regex("['\"\n\r\\s]"), "")
        val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)

        // Write to cache/share directory
        val shareDir = java.io.File(cacheDir, "share").also { it.mkdirs() }
        val file = java.io.File(shareDir, "whaleup-share-${System.currentTimeMillis()}.png")
        file.writeBytes(decodedBytes)
        Log.d(TAG, "Share file written: ${file.absolutePath}, size=${file.length()} bytes")

        // Get FileProvider URI — authority must match AndroidManifest.xml exactly
        val authority = "${applicationContext.packageName}.gameshub.fileprovider"
        val contentUri = FileProvider.getUriForFile(this, authority, file)
        Log.d(TAG, "Content URI: $contentUri")

        // Build the share intent
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, text)
            payload.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            // Critical: set ClipData AND the read URI flag together on the same intent
            clipData = android.content.ClipData.newRawUri("", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Grant URI permission to all apps that can handle this intent
        // (chooser doesn't propagate flags automatically on all API levels)
        val chooser = Intent.createChooser(shareIntent, payload.title ?: "Share")
        
        // Query all resolvers and grant them URI permission explicitly
        val resolvers = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                shareIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        
        resolvers.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Log.d(TAG, "Granted URI permission to: $packageName")
        }

        startActivity(chooser)

        // Schedule cleanup after share sheet is likely dismissed
        android.os.Handler(mainLooper).postDelayed({
            if (file.delete()) {
                Log.d(TAG, "Cleaned up share file: ${file.name}")
            }
        }, 30_000L) // 30s — generous buffer for slow share targets

        bridge.sendMessageToWebView(
            BiomeMessageType.SHARE,
            BiomeMessageAction.SHARE_SUCCESS,
            mapOf("success" to true)
        )

    } catch (e: Exception) {
        Log.e(TAG, "Share failed", e)
        bridge.sendMessageToWebView(
            BiomeMessageType.SHARE,
            BiomeMessageAction.SHARE_FAILED,
            mapOf("error" to (e.message ?: "Share failed"), "fallback" to "clipboard")
        )
        reportSdkError(
            SDKError(
                type = BiomeMessageType.LOAD_FAILURE,
                action = BiomeMessageAction.SHARE_FAILED,
                data = mapOf("reason" to (e.message ?: "Share failed"), "retryable" to true)
            )
        )
    }
}

    private fun handleCopyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Biome", text))
            Log.d(TAG, "Copied to clipboard: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard copy failed", e)
        }
    }

    // endregion

    // region Network Monitoring

    private fun sendMultiplayerSetupError(message: String, gameId: String?) {
        Log.e(TAG, message)
        val data = mapOf(
            "reason" to message,
            "retryable" to false,
            "gameId" to gameId
        )
        bridge.sendMessageToWebView(
            BiomeMessageType.MULTIPLAYER,
            BiomeMessageAction.MP_ERROR,
            mapOf(
                "code" to "MULTIPLAYER_NOT_CONFIGURED",
                "message" to message,
                "retryable" to false
            )
        )
        reportSdkError(
            SDKError(
                type = BiomeMessageType.LOAD_FAILURE,
                action = "multiplayerNotConfigured",
                data = data
            )
        )
    }

    private fun registerNetworkListener() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                runOnUiThread {
                    if (SdkErrorPresenter.isInternetAvailable(this@HubWebViewActivity)) return@runOnUiThread
                    if (networkInterruptionActive) return@runOnUiThread
                    networkInterruptionActive = true
                    Log.w(TAG, "Network lost")
                    bridge.sendMessageToWebView(
                        BiomeMessageType.NETWORK_INTERRUPTION,
                        BiomeMessageAction.NETWORK_INTERRUPTED,
                        mapOf("timestamp" to System.currentTimeMillis(), "reason" to "No internet connection")
                    )
                    reportSdkError(
                        SDKError(
                            type = BiomeMessageType.NETWORK_INTERRUPTION,
                            action = BiomeMessageAction.NETWORK_INTERRUPTED,
                            data = mapOf("reason" to "Network connection lost", "retryable" to true)
                        )
                    )
                }
            }

            override fun onAvailable(network: Network) {
                runOnUiThread {
                    Log.d(TAG, "Network restored")
                    multiplayerModule?.connect(reconnect = true)
                    if (multiplayerModule == null) notifyNetworkRestoredIfNeeded()
                }
            }
        }

        try {
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: SecurityException) {
            networkCallback = null
            Log.e(TAG, "Missing ACCESS_NETWORK_STATE permission", e)
        }
    }

    private fun notifyNetworkRestoredIfNeeded() {
        SdkErrorPresenter.dismissInternetErrorDialog()
        if (!networkInterruptionActive) return
        networkInterruptionActive = false
        bridge.sendMessageToWebView(
            BiomeMessageType.NETWORK_INTERRUPTION,
            BiomeMessageAction.NETWORK_RESTORED,
            mapOf("timestamp" to System.currentTimeMillis())
        )
    }

    private fun handleMultiplayerReconnectExpired(timeoutMs: Long) {
        networkInterruptionActive = false
        val data = mapOf(
            "reason" to "Multiplayer reconnect window expired. Please close and restart the game.",
            "retryable" to false,
            "reconnectTimeoutMs" to timeoutMs
        )
        bridge.sendMessageToWebView(
            BiomeMessageType.NETWORK_INTERRUPTION,
            BiomeMessageAction.NETWORK_LOAD_ERROR,
            data
        )
        reportSdkError(
            SDKError(
                type = BiomeMessageType.NETWORK_INTERRUPTION,
                action = BiomeMessageAction.NETWORK_LOAD_ERROR,
                data = data
            )
        )
    }

    private fun unregisterNetworkListener() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { callback ->
            runCatching { cm.unregisterNetworkCallback(callback) }
                .onFailure { Log.w(TAG, "Unable to unregister network callback", it) }
        }
        networkCallback = null
    }

    // endregion

    // region Helpers

    private fun notifySdkError(error: BiomeSdkError) {
        reportSdkError(
            SDKError(type = error.type, action = error.action, data = error.data)
        )
    }

    private fun reportSdkError(error: SDKError) {
        SdkErrorPresenter.report(this, error)
    }

    private fun notifySdkEvent(event: BiomeSdkEvent) {
        val eventData = enrichSdkEventData(event.data)
        GamesHubSession.props?.onWhaleupSDKEvent?.invoke(
            SDKEvent(type = event.type, action = event.action, message = event.message, data = eventData)
        )
    }

    private fun enrichSdkEventData(data: Map<String, Any?>?): Map<String, Any?> {
        val enriched = data?.toMutableMap() ?: mutableMapOf()
        val userId = enriched["user_id"]
            ?: enriched["userId"]
            ?: BiomeState.getUserConfig()?.userId
        val sessionId = enriched["session_id"]
            ?: enriched["sessionId"]
            ?: BiomeState.getSessionId()
        val gameId = enriched["game_id"]
            ?: enriched["gameId"]
            ?: BiomeState.getCurrentGameId()

        enriched["user_id"] = userId ?: ""
        enriched["session_id"] = sessionId ?: ""
        enriched["game_id"] = gameId ?: ""
        return enriched
    }

    private fun injectContext(view: WebView?) {
        val contextJson = ContextProvider.getContextJson()
        val js = """
            (function() {
                window.WhaleContext = $contextJson;
                window.WhaleBridgeEnabled = true;
                
                // Shim for ReactNativeWebView if content expects it
                if (!window.ReactNativeWebView) {
                    window.ReactNativeWebView = {
                        postMessage: function(data) {
                            if (window.WhaleBridge) window.WhaleBridge.postMessage(data);
                            else if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage !== this.postMessage) {
                                // Prevent infinite recursion if native injected it later
                                window.ReactNativeWebView.postMessage(data);
                            }
                        }
                    };
                }
                window.WhaleContext = $contextJson;
                console.log('WhaleContext injected and shims applied');
                if (window.onWhaleContextReady) {
                    window.onWhaleContextReady(window.WhaleContext);
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    // endregion
}
