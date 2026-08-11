package com.whaleup.gameshub.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.AppEntry
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.data.CatalogLoader
import com.whaleup.gameshub.data.CatalogLoaderCallback
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.data.HubCatalog
import com.whaleup.gameshub.data.SDKError
import com.whaleup.gameshub.launcher.BiomeSdkProps
import com.whaleup.gameshub.messaging.toMap
import com.whaleup.gameshub.network.APIBridge
import com.whaleup.gameshub.network.APICallback
import com.whaleup.gameshub.util.SdkErrorPresenter
import com.whaleup.gameshub.webview.HubWebViewActivity
import org.json.JSONObject

class GamesHubFragment : Fragment(), GamesHubSession.ThemeChangeListener {

    private var catalogGames: List<AppEntry> = emptyList()
    private var isGameLaunchInProgress = false
    private var isLoadingCatalog = false
    private var activeTab: String = "games" // "games" or "leaderboard"

    private lateinit var gameCardAdapter: GameCardAdapter
    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    private var bannerHandler: Handler = Handler(Looper.getMainLooper())
    private var bannerRunnable: Runnable? = null
    private var bannerUrls: List<String> = emptyList()

    private var _props: BiomeSdkProps? = null
    var props: BiomeSdkProps?
        get() = _props
        set(value) {
            _props = value?.let { newProps ->
                val resolvedProps = if (newProps.userConfig.userId.isBlank()) {
                    BiomeState.getUserConfig()?.let { newProps.copy(userConfig = it) } ?: _props
                } else {
                    newProps
                }
                resolvedProps?.let { GamesHubSession.resolveSessionId(it) }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val theme = props?.currentTheme() ?: GamesHubSession.theme
        val themeResId = if (theme.lowercase() == "dark") R.style.Theme_GamesHub_Dark else R.style.Theme_GamesHub_Light
        val contextThemeWrapper = android.view.ContextThemeWrapper(requireContext(), themeResId)
        val localInflater = inflater.cloneInContext(contextThemeWrapper)
        return localInflater.inflate(R.layout.fragment_games_hub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load background image from assets
        try {
            val ivBg = view.findViewById<ImageView>(R.id.ivHubBg)
            val isAsset = requireContext().assets.open("onboarding/bg.png")
            val bitmap = BitmapFactory.decodeStream(isAsset)
            ivBg.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.w("GamesHubFragment", "Could not load onboarding/bg.png from assets", e)
        }

        // Handle window insets (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(top = systemBars.top)
            insets
        }

        setupUI(view)

        val errorContainer = view.findViewById<View>(R.id.flErrorContainer)
        val btnRetry = view.findViewById<View>(R.id.btnRetry)
        btnRetry?.setOnClickListener {
            errorContainer?.visibility = View.GONE
            view.findViewById<View>(R.id.scrollGamesTab)?.visibility = View.VISIBLE
            retryAfterInternetError()
        }

        initSdk()
        view.requestApplyInsets()
    }

    private fun setupUI(view: View) {
        // 1. Setup 2-Column Game Cards Grid
        val rvGameCards = view.findViewById<RecyclerView>(R.id.rvGameCards)
        rvGameCards.layoutManager = GridLayoutManager(requireContext(), 2)
        gameCardAdapter = GameCardAdapter(emptyList()) { game, pos ->
            openGame(game, pos)
        }
        rvGameCards.adapter = gameCardAdapter

        // 2. Setup Banner ViewPager2
        val vpBanner = view.findViewById<ViewPager2>(R.id.vpBannerPager)
        bannerAdapter = BannerAdapter(emptyList()) { url, index ->
            // Banner click handler
        }
        vpBanner.adapter = bannerAdapter

        // 3. Setup Leaderboard List
        val rvLeaderboard = view.findViewById<RecyclerView>(R.id.rvLeaderboard)
        rvLeaderboard.layoutManager = LinearLayoutManager(requireContext())
        leaderboardAdapter = LeaderboardAdapter(emptyList())
        rvLeaderboard.adapter = leaderboardAdapter

        // 4. Setup Floating Bottom Navigation Bar
        val tabGames = view.findViewById<View>(R.id.tabGames)
        val tabLeaderboard = view.findViewById<View>(R.id.tabLeaderboard)

        tabGames.setOnClickListener { switchTab("games") }
        tabLeaderboard.setOnClickListener { switchTab("leaderboard") }

        updateTabState(view, "games", animate = false)

        // Load sample/initial data for Leaderboard
        loadSampleLeaderboard()
    }

    private fun switchTab(tab: String) {
        if (isLoadingCatalog || view?.findViewById<View>(R.id.flSkeletonContainer)?.visibility == View.VISIBLE) {
            return
        }
        if (activeTab == tab) return
        activeTab = tab
        view?.let { updateTabState(it, tab, animate = true) }

        if (tab == "leaderboard") {
            fetchLeaderboardFromApi()
        }
    }

    private fun updateTabState(view: View, tab: String, animate: Boolean) {
        val scrollGamesTab = view.findViewById<View>(R.id.scrollGamesTab)
        val containerLeaderboardTab = view.findViewById<View>(R.id.containerLeaderboardTab)
        val viewActiveNavPill = view.findViewById<View>(R.id.viewActiveNavPill)
        val flBottomNav = view.findViewById<View>(R.id.flBottomNav)

        val ivTabGamesIcon = view.findViewById<ImageView>(R.id.ivTabGamesIcon)
        val tvTabGamesText = view.findViewById<TextView>(R.id.tvTabGamesText)
        val ivTabLeaderboardIcon = view.findViewById<ImageView>(R.id.ivTabLeaderboardIcon)
        val tvTabLeaderboardText = view.findViewById<TextView>(R.id.tvTabLeaderboardText)

        val isSkeletonVisible = view.findViewById<View>(R.id.flSkeletonContainer)?.visibility == View.VISIBLE

        if (tab == "games") {
            if (!isSkeletonVisible) scrollGamesTab.visibility = View.VISIBLE
            containerLeaderboardTab.visibility = View.GONE

            ivTabGamesIcon.setColorFilter(Color.WHITE)
            tvTabGamesText.setTextColor(Color.WHITE)
            ivTabLeaderboardIcon.setColorFilter(Color.parseColor("#0A3D68"))
            tvTabLeaderboardText.setTextColor(Color.parseColor("#0A3D68"))
        } else {
            scrollGamesTab.visibility = View.GONE
            if (!isSkeletonVisible) containerLeaderboardTab.visibility = View.VISIBLE

            ivTabGamesIcon.setColorFilter(Color.parseColor("#0A3D68"))
            tvTabGamesText.setTextColor(Color.parseColor("#0A3D68"))
            ivTabLeaderboardIcon.setColorFilter(Color.WHITE)
            tvTabLeaderboardText.setTextColor(Color.WHITE)
        }

        flBottomNav.post {
            val totalWidth = flBottomNav.width - dp(8)
            val halfWidth = totalWidth / 2
            val targetX = if (tab == "games") 0f else halfWidth.toFloat()

            viewActiveNavPill.layoutParams = viewActiveNavPill.layoutParams.apply {
                width = halfWidth
            }

            if (animate) {
                ObjectAnimator.ofFloat(viewActiveNavPill, "translationX", targetX).setDuration(260).start()
            } else {
                viewActiveNavPill.translationX = targetX
            }
        }
    }

    private fun setupBannerAutoScroll(urls: List<String>) {
        bannerUrls = urls
        bannerAdapter.updateUrls(urls)
        setupBannerDots(urls.size)

        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }

        if (urls.size <= 1) return

        val vpBanner = view?.findViewById<ViewPager2>(R.id.vpBannerPager) ?: return
        bannerRunnable = object : Runnable {
            override fun run() {
                val current = vpBanner.currentItem
                val next = (current + 1) % urls.size
                vpBanner.setCurrentItem(next, true)
                updateBannerDots(next)
                bannerHandler.postDelayed(this, 5000)
            }
        }
        bannerHandler.postDelayed(bannerRunnable!!, 5000)

        vpBanner.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateBannerDots(position)
            }
        })
    }

    private fun setupBannerDots(count: Int) {
        val container = view?.findViewById<LinearLayout>(R.id.llBannerDots) ?: return
        container.removeAllViews()
        if (count <= 1) return

        repeat(count) { i ->
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 0) dp(18) else dp(6),
                    dp(6)
                ).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                background = createDotDrawable(i == 0)
            }
            container.addView(dot)
        }
    }

    private fun updateBannerDots(position: Int) {
        val container = view?.findViewById<LinearLayout>(R.id.llBannerDots) ?: return
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val isSelected = i == position
            dot.layoutParams = dot.layoutParams.apply {
                width = if (isSelected) dp(18) else dp(6)
            }
            dot.background = createDotDrawable(isSelected)
        }
    }

    private fun createDotDrawable(isSelected: Boolean): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(3).toFloat()
            setColor(if (isSelected) Color.WHITE else Color.parseColor("#66FFFFFF"))
        }
    }

    private fun loadSampleLeaderboard() {
        val currentUserId = BiomeState.getUserProfile()?.basic?.userId ?: "Player1"
        val sampleItems = listOf(
            LeaderboardItemData("LeaderPro", 1, 3500, 3600),
            LeaderboardItemData("GameMaster", 2, 2800, 2700),
            LeaderboardItemData("StarRunner", 3, 2100, 1800),
            LeaderboardItemData(currentUserId, 4, 1500, 1200),
            LeaderboardItemData("CosmicKing", 5, 1200, 950),
            LeaderboardItemData("LuckyPlayer", 6, 950, 700)
        )
        leaderboardAdapter.updateData(sampleItems)
    }

    private fun fetchLeaderboardFromApi() {
        APIBridge.getLeaderboard(mapOf("page" to 1, "limit" to 10), object : APICallback {
            override fun onSuccess(response: String) {
                try {
                    val json = JSONObject(response)
                    val itemsArr = json.optJSONArray("items")
                    if (itemsArr != null && itemsArr.length() > 0) {
                        val list = mutableListOf<LeaderboardItemData>()
                        for (i in 0 until itemsArr.length()) {
                            val obj = itemsArr.getJSONObject(i)
                            list.add(
                                LeaderboardItemData(
                                    userId = obj.optString("userId", "User_$i"),
                                    ranking = obj.optInt("ranking", i + 1),
                                    score = obj.optInt("score", 1000 - i * 50),
                                    playtime = obj.optInt("playtime", 600)
                                )
                            )
                        }
                        activity?.runOnUiThread {
                            leaderboardAdapter.updateData(list)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GamesHubFragment", "Failed to parse leaderboard response", e)
                }
            }

            override fun onError(code: Int, message: String) {
                Log.w("GamesHubFragment", "Leaderboard fetch error: $code $message")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        isGameLaunchInProgress = false
        props?.let {
            GamesHubSession.props = it
        }
        if (catalogGames.isNotEmpty()) {
            fireHubViewedEvent()
        }
    }

    override fun onStart() {
        super.onStart()
        GamesHubSession.addThemeChangeListener(this)
    }

    override fun onStop() {
        GamesHubSession.removeThemeChangeListener(this)
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        super.onStop()
    }

    override fun onThemeChanged(theme: String) {
        props = props?.copy(theme = theme)
        if (catalogGames.isNotEmpty()) {
            gameCardAdapter.updateList(catalogGames)
        }
    }

    fun retryAfterInternetError() {
        initSdk()
    }

    private fun fireHubViewedEvent() {
        val userId = GamesHubSession.props?.userConfig?.userId?.takeIf { it.isNotBlank() }
        val sessionId = GamesHubSession.props?.userConfig?.sessionId
        GamesHubSession.props?.onBiomeEvent?.invoke(
            com.whaleup.gameshub.data.SDKEvent(
                type = com.whaleup.gameshub.data.BiomeMessageType.HUB_EVENT,
                action = com.whaleup.gameshub.data.BiomeMessageAction.HUB_VIEWED,
                message = "Games Hub viewed with ${catalogGames.size} game(s) available",
                data = buildMap {
                    put("tab_name", "games_hub")
                    if (userId != null) put("user_id", userId)
                    if (sessionId != null) put("session_id", sessionId)
                    put("games_available_count", catalogGames.size)
                }
            )
        )
    }

    private fun initSdk() {
        val config = props?.userConfig?.takeIf { it.userId.isNotBlank() }
            ?: GamesHubSession.props?.userConfig?.takeIf { it.userId.isNotBlank() }
            ?: BiomeState.getUserConfig()
            ?: return
        val context = context ?: return
        if (!SdkErrorPresenter.isInternetAvailable(context)) {
            reportSdkError(
                type = com.whaleup.gameshub.data.BiomeMessageType.NETWORK_INTERRUPTION,
                action = com.whaleup.gameshub.data.BiomeMessageAction.NETWORK_INTERRUPTED,
                data = mapOf("reason" to "No internet connection", "retryable" to true)
            )
            return
        }

        APIBridge.baseUrl = config.apiBaseUrl
        APIBridge.authToken = config.authToken
        APIBridge.userAgent = config.userAgent
        APIBridge.timezone = config.timezone
        APIBridge.setSessionId(BiomeState.getSessionId() ?: config.sessionId)

        APIBridge.getUserProfile(
            mapOf("userId" to config.userId, "userName" to config.name, "avatarUrl" to config.avatar),
            object : APICallback {
                override fun onSuccess(response: String) {
                    try {
                        val profileMap = JSONObject(response).toMap()
                        BiomeState.setUserProfile(profileMap, "server")
                    } catch (e: Exception) {
                        Log.e("GamesHubFragment", "Error parsing user profile", e)
                    }
                    loadCatalog()
                }

                override fun onError(code: Int, message: String) {
                    loadCatalog()
                }
            }
        )
    }

    private fun loadCatalog() {
        isLoadingCatalog = true
        activity?.runOnUiThread {
            view?.findViewById<View>(R.id.flSkeletonContainer)?.visibility = View.VISIBLE
            view?.findViewById<View>(R.id.scrollGamesTab)?.visibility = View.GONE
            view?.findViewById<View>(R.id.containerLeaderboardTab)?.visibility = View.GONE
        }
        CatalogLoader.loadFromNetwork(object : CatalogLoaderCallback {
            override fun onSuccess(catalog: HubCatalog) {
                isLoadingCatalog = false
                activity?.runOnUiThread {
                    view?.findViewById<View>(R.id.flSkeletonContainer)?.visibility = View.GONE
                    if (activeTab == "leaderboard") {
                        view?.findViewById<View>(R.id.containerLeaderboardTab)?.visibility = View.VISIBLE
                        view?.findViewById<View>(R.id.scrollGamesTab)?.visibility = View.GONE
                    } else {
                        view?.findViewById<View>(R.id.scrollGamesTab)?.visibility = View.VISIBLE
                        view?.findViewById<View>(R.id.containerLeaderboardTab)?.visibility = View.GONE
                    }
                    catalogGames = catalog.games
                    gameCardAdapter.updateList(catalog.games)

                    // Setup Banner: use heroBannerUrls if present, else fallback to game banner images
                    var heroBanners = catalog.heroBannerUrls.filter { it.isNotBlank() }
                    if (heroBanners.isEmpty()) {
                        heroBanners = catalog.games.mapNotNull { it.bannerImageUrl.takeIf { u -> u.isNotBlank() } }
                    }

                    val bannerContainer = view?.findViewById<View>(R.id.bannerContainer)
                    if (heroBanners.isNotEmpty()) {
                        bannerContainer?.visibility = View.VISIBLE
                        setupBannerAutoScroll(heroBanners)
                    } else {
                        bannerContainer?.visibility = View.GONE
                    }

                    fireHubViewedEvent()
                    
                    // Check and show Daily Login Overlay if eligible
                    view?.findViewById<com.whaleup.gameshub.ui.overlay.DailyLoginOverlayView>(R.id.vDailyLoginOverlay)?.showIfEligible()

                    GamesHubSession.props?.onMessage?.invoke(
                        com.whaleup.gameshub.data.JsMessage(
                            type = com.whaleup.gameshub.data.BiomeMessageType.NAVIGATION,
                            action = com.whaleup.gameshub.data.BiomeMessageAction.HUB_LOADED
                        )
                    )
                }
            }

            override fun onError(error: Exception) {
                isLoadingCatalog = false
                activity?.runOnUiThread {
                    view?.findViewById<View>(R.id.flSkeletonContainer)?.visibility = View.GONE
                    if (activeTab == "leaderboard") {
                        view?.findViewById<View>(R.id.containerLeaderboardTab)?.visibility = View.VISIBLE
                        view?.findViewById<View>(R.id.scrollGamesTab)?.visibility = View.GONE
                    } else {
                        view?.findViewById<View>(R.id.scrollGamesTab)?.visibility = View.VISIBLE
                        view?.findViewById<View>(R.id.containerLeaderboardTab)?.visibility = View.GONE
                    }
                    Log.e("GamesHubFragment", "Error loading catalog", error)
                }
            }
        })
    }

    fun showGameWinOverlay(gameName: String, coinsEarned: Int) {
        activity?.runOnUiThread {
            // For Testing: Use values showGameWinOverlay("Ludo", 100) || actual values: gameName, coinsEarned
            view?.findViewById<com.whaleup.gameshub.ui.overlay.GameWinOverlayView>(R.id.vGameWinOverlay)?.show(gameName, coinsEarned)
        }
    }

    private fun reportSdkError(type: String, action: String, data: Map<String, Any?>) {
        val error = SDKError(type = type, action = action, data = data)
        GamesHubSession.props?.onBiomeError?.invoke(error)

        val context = context ?: return
        if (SdkErrorPresenter.isInternetError(context, error)) {
            activity?.runOnUiThread {
                view?.findViewById<View>(R.id.scrollGamesTab)?.visibility = View.GONE
                val errorContainer = view?.findViewById<View>(R.id.flErrorContainer)
                errorContainer?.visibility = View.VISIBLE

                val errorCode = SdkErrorPresenter.errorCodeFor(context, error)
                val reason = error.data?.get("reason")?.toString()?.takeIf { it.isNotBlank() }
                val message = SdkErrorPresenter.formatInternetMessage(reason)
                view?.findViewById<TextView>(R.id.tvErrorTitle)?.text = "Internet Connection Issue ($errorCode)"
                view?.findViewById<TextView>(R.id.tvErrorMessage)?.text = message
            }
        } else {
            SdkErrorPresenter.report(context, error)
        }
    }

    private fun openGame(game: AppEntry, cardPosition: Int = 0) {
        if (isGameLaunchInProgress) return
        isGameLaunchInProgress = true

        val userId = GamesHubSession.props?.userConfig?.userId?.takeIf { it.isNotBlank() }
            ?: BiomeState.getUserConfig()?.userId?.takeIf { it.isNotBlank() }

        GamesHubSession.props?.onBiomeEvent?.invoke(
            com.whaleup.gameshub.data.SDKEvent(
                type = com.whaleup.gameshub.data.BiomeMessageType.HUB_EVENT,
                action = com.whaleup.gameshub.data.BiomeMessageAction.HUB_GAME_CARD_TAPPED,
                message = "Game card tapped: ${game.name} at position $cardPosition",
                data = buildMap {
                    put("game_id", game.id)
                    put("game_name", game.name)
                    put("card_position", cardPosition)
                    if (userId != null) put("user_id", userId)
                }
            )
        )

        val launchMsg = com.whaleup.gameshub.messaging.BiomeMessage(
            type = com.whaleup.gameshub.data.BiomeMessageType.NAVIGATION,
            action = com.whaleup.gameshub.data.BiomeMessageAction.LAUNCH_GAME,
            data = mapOf("gameId" to game.id, "gameName" to game.name)
        )

        val actions = com.whaleup.gameshub.messaging.MessageRouter.route(launchMsg)
        var didStartGame = false

        actions.forEach { action ->
            when (action) {
                is com.whaleup.gameshub.messaging.RouteAction.Bubble -> {
                    val msg = action.message
                    GamesHubSession.props?.onMessage?.invoke(
                        com.whaleup.gameshub.data.JsMessage(msg.type, msg.action, msg.data)
                    )
                }

                is com.whaleup.gameshub.messaging.RouteAction.LoadGame -> {
                    val intent = Intent(requireContext(), HubWebViewActivity::class.java)
                    intent.putExtra("ENTRY_URL", game.entryUrl)
                    intent.putExtra("GAME_ID", game.id)
                    intent.putExtra("GAME_NAME", game.name)
                    startActivity(intent)
                    didStartGame = true
                }

                else -> { }
            }
        }

        if (!didStartGame) {
            isGameLaunchInProgress = false
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        fun newInstance(props: BiomeSdkProps? = null): GamesHubFragment {
            val fragment = GamesHubFragment()
            fragment.props = props
            return fragment
        }
    }
}
