package com.whaleup.gameshub.ui

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.AppEntry
import com.whaleup.gameshub.data.CatalogLoader
import com.whaleup.gameshub.data.CatalogLoaderCallback
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.data.HubCatalog
import com.whaleup.gameshub.data.SDKError
import com.whaleup.gameshub.launcher.BiomeSdkProps
import com.whaleup.gameshub.util.ImageLoader
import com.whaleup.gameshub.util.SdkErrorPresenter
import com.whaleup.gameshub.webview.HubWebViewActivity
import org.json.JSONObject
import com.whaleup.gameshub.messaging.toMap

class GamesHubFragment : Fragment(), GamesHubSession.ThemeChangeListener {

    private var catalogGames: List<AppEntry> = emptyList()
    private var isGameLaunchInProgress = false

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
        // Resolve SDK theme and wrap context to ensure attributes (ghBackgroundColor, etc.) are available
        val theme = props?.currentTheme() ?: GamesHubSession.theme
        val themeResId = if (theme.lowercase() == "dark") R.style.Theme_GamesHub_Dark else R.style.Theme_GamesHub_Light
        val contextThemeWrapper = android.view.ContextThemeWrapper(requireContext(), themeResId)
        val localInflater = inflater.cloneInContext(contextThemeWrapper)
        return localInflater.inflate(R.layout.fragment_games_hub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyThemeBackground(view)
        view.findViewById<View>(R.id.gamesListContainer)?.doOnLayout {
            applyThemeBackground(view)
        }

        // Handle window insets (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )
            
            // Apply top padding to top view (status bar)
            v.updatePadding(top = systemBars.top)

            // Return insets so children can receive them
            insets
        }

        setupUI(view)
        
        val errorContainer = view.findViewById<View>(R.id.errorContainer)
        val btnRetry = view.findViewById<View>(R.id.btnRetry)
        btnRetry?.setOnClickListener {
            errorContainer?.visibility = View.GONE
            view.findViewById<View>(R.id.contentScroll)?.visibility = View.VISIBLE
            retryAfterInternetError()
        }

        initSdk()
        
        // Ensure insets are dispatched to this fragment's view
        view.requestApplyInsets()
    }

    override fun onResume() {
        super.onResume()
        isGameLaunchInProgress = false
        // Re-apply this fragment's specific props to the shared session when becoming visible
        props?.let {
            GamesHubSession.props = it
        }

        refreshThemeDependentUi()

        // hub_viewed: fires on return visits (catalog already loaded).
        // On first load it fires from loadCatalog once the real count is known.
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
        super.onStop()
    }

    override fun onThemeChanged(theme: String) {
        props = props?.copy(theme = theme)
        refreshThemeDependentUi()
        if (catalogGames.isNotEmpty()) {
            renderGames(catalogGames)
        } else {
            renderSkeletonCards()
        }
    }

    fun retryAfterInternetError() {
        renderSkeletonCards()
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

        com.whaleup.gameshub.network.APIBridge.baseUrl = config.apiBaseUrl
        com.whaleup.gameshub.network.APIBridge.authToken = config.authToken
        com.whaleup.gameshub.network.APIBridge.userAgent = config.userAgent
        com.whaleup.gameshub.network.APIBridge.timezone = config.timezone
        com.whaleup.gameshub.network.APIBridge.setSessionId(BiomeState.getSessionId() ?: config.sessionId)

        com.whaleup.gameshub.network.APIBridge.getUserProfile(
            mapOf("userId" to config.userId, "userName" to config.name, "avatarUrl" to config.avatar),
            object : com.whaleup.gameshub.network.APICallback {
                override fun onSuccess(response: String) {
                    try {
                        val profileMap = JSONObject(response).toMap()
                        com.whaleup.gameshub.data.BiomeState.setUserProfile(profileMap, "server")
                    } catch (e: Exception) {
                        Log.e("GamesHubFragment", "Error parsing user profile", e)
                        reportSdkError(
                            type = com.whaleup.gameshub.data.BiomeMessageType.LOAD_FAILURE,
                            action = com.whaleup.gameshub.data.BiomeMessageAction.INTERNAL_ERROR,
                            data = mapOf("reason" to "Failed to parse user profile", "message" to (e.message ?: "Unknown error"))
                        )
                    }
                    loadCatalog()
                }

                override fun onError(code: Int, message: String) {
                    reportSdkError(
                        type = com.whaleup.gameshub.data.BiomeMessageType.LOAD_FAILURE,
                        action = com.whaleup.gameshub.data.BiomeMessageAction.INTERNAL_ERROR,
                        data = mapOf("reason" to "Failed to load user profile", "code" to code, "message" to message)
                    )
                    loadCatalog() // Still load catalog even if profile fails
                }
            }
        )
    }

    private fun setupUI(view: View) {
        disableMotionEventSplitting(view)
        renderSkeletonCards()
        applyGameVignettes(view)
    }

    private fun disableMotionEventSplitting(view: View) {
        if (view is ViewGroup) {
            view.isMotionEventSplittingEnabled = false
            for (i in 0 until view.childCount) {
                disableMotionEventSplitting(view.getChildAt(i))
            }
        }
    }

    private fun loadCatalog() {
        CatalogLoader.loadFromNetwork(object : CatalogLoaderCallback {
            override fun onSuccess(catalog: HubCatalog) {
                activity?.runOnUiThread {
                    view?.findViewById<View>(R.id.contentScroll)?.visibility = View.VISIBLE
                    catalogGames = catalog.games
                    renderGames(catalog.games)

                    // hub_viewed: fires on initial load once the real game count is known
                    fireHubViewedEvent()

                    GamesHubSession.props?.onMessage?.invoke(
                        com.whaleup.gameshub.data.JsMessage(
                            type = com.whaleup.gameshub.data.BiomeMessageType.NAVIGATION,
                            action = com.whaleup.gameshub.data.BiomeMessageAction.HUB_LOADED
                        )
                    )
                }
            }

            override fun onError(error: Exception) {
                activity?.runOnUiThread {
                    view?.findViewById<View>(R.id.contentScroll)?.visibility = View.VISIBLE
                    
                    Log.e("GamesHubFragment", "Error loading catalog", error)
                    reportSdkError(
                        type = com.whaleup.gameshub.data.BiomeMessageType.LOAD_FAILURE,
                        action = com.whaleup.gameshub.data.BiomeMessageAction.HUB_LOAD_ERROR,
                        data = mapOf("reason" to "Failed to load game catalog", "message" to (error.message ?: "Unknown error"))
                    )
                }
            }
        })
    }

    private fun reportSdkError(type: String, action: String, data: Map<String, Any?>) {
        val error = SDKError(type = type, action = action, data = data)
        GamesHubSession.props?.onBiomeError?.invoke(error)

        val context = context ?: return
        if (SdkErrorPresenter.isInternetError(context, error)) {
            activity?.runOnUiThread {
                view?.findViewById<View>(R.id.contentScroll)?.visibility = View.GONE
                val errorContainer = view?.findViewById<View>(R.id.errorContainer)
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

    private fun applyThemeBackground(view: View) {
        val fallbackRadius = resources.displayMetrics.density * 168f
        val radius = getFirstCardTopInRoot(view)?.toFloat() ?: fallbackRadius

        view.background = GamesHubBackgroundDrawable(isDarkTheme(), radius)
    }

    private class GamesHubBackgroundDrawable(
        private val isDark: Boolean,
        private val cardTop: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val startColor = Color.parseColor(if (isDark) "#004F73" else "#BCEAFF")
        private val endColor = Color.parseColor(if (isDark) "#0C191F" else "#FFFFFF")
        private val matrix = Matrix()

        override fun draw(canvas: Canvas) {
            val width = bounds.width().toFloat()
            val height = bounds.height().toFloat()

            if (cardTop > 0 && width > 0) {
                val rx = width / 2f
                val ry = cardTop

                val shader = RadialGradient(
                    0f, 0f, 1f,
                    intArrayOf(startColor, endColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )

                matrix.reset()
                matrix.setScale(rx, ry)
                matrix.postTranslate(width / 2f, 0f)
                shader.setLocalMatrix(matrix)

                paint.shader = shader
                canvas.drawRect(0f, 0f, width, cardTop, paint)

                // Draw solid endColor from cardTop to height
                paint.shader = null
                paint.color = endColor
                canvas.drawRect(0f, cardTop, width, height, paint)
            } else {
                // Fallback: entire background is solid endColor
                paint.shader = null
                paint.color = endColor
                canvas.drawRect(0f, 0f, width, height, paint)
            }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun refreshThemeDependentUi() {
        view?.let {
            applyThemeBackground(it)
            applyGameVignettes(it)
        }
    }

    private fun applyGameVignettes(view: View) {
        findTaggedViews(view, TAG_VIGNETTE_TOP).forEach {
            it.background = GameBgVignetteDrawable(isTop = true, isDark = isDarkTheme())
        }
        findTaggedViews(view, TAG_VIGNETTE_BOTTOM).forEach {
            it.background = GameBgVignetteDrawable(isTop = false, isDark = isDarkTheme())
        }
    }

    private fun getFirstCardTopInRoot(root: View): Int? {
        val card = root.findViewById<LinearLayout>(R.id.gamesListContainer)?.getChildAt(0) ?: return null
        if (root.width == 0 || card.width == 0) return null

        val rootLocation = IntArray(2)
        val cardLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        card.getLocationOnScreen(cardLocation)
        return (cardLocation[1] - rootLocation[1]).takeIf { it > 0 }
    }

    private fun renderGames(games: List<AppEntry>) {
        val container = view?.findViewById<LinearLayout>(R.id.gamesListContainer) ?: return
        container.removeAllViews()
        games.forEachIndexed { index, game ->
            container.addView(createGameSection(game, index))
        }
        container.doOnLayout {
            refreshThemeDependentUi()
        }
    }

    private fun renderSkeletonCards() {
        val container = view?.findViewById<LinearLayout>(R.id.gamesListContainer) ?: return
        container.removeAllViews()
        repeat(2) { index ->
            container.addView(createSkeletonSection(index))
        }
        container.doOnLayout {
            refreshThemeDependentUi()
        }
    }

    private fun createSkeletonSection(index: Int): View {
        val section = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(289)
            ).apply {
                topMargin = if (index == 0) dp(30) else dp(18)
            }
            background = createSkeletonBgDrawable()
        }

        section.addView(View(requireContext()).apply {
            tag = TAG_VIGNETTE_TOP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(92),
                Gravity.TOP
            )
        })

        section.addView(View(requireContext()).apply {
            tag = TAG_VIGNETTE_BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(92),
                Gravity.BOTTOM
            )
        })

        val card = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
            }
            background = createRoundedCardBackground(
                radius = dp(22).toFloat(),
                strokeColor = Color.parseColor(if (isDarkTheme()) "#7C95A4" else "#A9D6E8"),
                strokeWidth = dp(1)
            )
            clipToOutline = true
        }

        val content = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = createSkeletonContentDrawable()
            setPadding(dp(15), dp(15), dp(15), dp(13))
        }

        content.addView(View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(dp(104), dp(28))
            background = createSkeletonPillDrawable()
        })

        content.addView(View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(114),
                dp(44),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            background = createSkeletonButtonDrawable()
        })

        card.addView(content)
        section.addView(card)
        return section
    }

    private fun createGameSection(game: AppEntry, index: Int): View {
        // card_position is 1-based per event design spec
        val cardPosition = index + 1

        val section = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(289)
            ).apply {
                topMargin = if (index == 0) dp(30) else dp(18)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { openGame(game, cardPosition) }
        }

        val bgUrl = game.bgUrl.trim()
        if (bgUrl.isNotEmpty()) {
            section.addView(ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(238),
                    Gravity.CENTER_VERTICAL
                )
                contentDescription = null
                scaleType = ImageView.ScaleType.CENTER_CROP
                ImageLoader.load(bgUrl, this)
            })
        } else {
            section.background = createMissingBgDrawable()
        }

        section.addView(View(requireContext()).apply {
            tag = TAG_VIGNETTE_TOP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(92),
                Gravity.TOP
            )
        })

        section.addView(View(requireContext()).apply {
            tag = TAG_VIGNETTE_BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(92),
                Gravity.BOTTOM
            )
        })

        val card = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
            }
            background = createRoundedCardBackground(radius = dp(22).toFloat())
            clipToOutline = true
            setOnClickListener { openGame(game, cardPosition) }
        }

        val content = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // GameCardDrawable is the card DIV above the game bg image:
            // linear gradient fill @10% opacity, noise @15%, dual inner shadow, no stroke
            background = GameCardDrawable(dp(22).toFloat())
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setPadding(dp(15), dp(15), dp(15), dp(13))
        }

        createBadgeText(game)?.let(content::addView)
        content.addView(createLogoOrNameView(game))
        content.addView(TextView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            background = requireContext().getDrawable(R.drawable.bg_games_button)
            gravity = Gravity.CENTER
            minWidth = dp(114)
            text = "Play Now"
            setTextColor(Color.parseColor("#0A0F14"))
            textSize = 16f
            isClickable = true
            isFocusable = true
            setOnClickListener { openGame(game, cardPosition) }
        })

        card.addView(content)
        section.addView(card)

        return section
    }

    private class GameBgVignetteDrawable(
        private val isTop: Boolean,
        private val isDark: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val width = bounds.width().toFloat()
            val height = bounds.height().toFloat()
            if (width <= 0f || height <= 0f) return

            val colors = if (!isDark && isTop) {
                intArrayOf(
                    Color.argb(0, 0xFF, 0xFF, 0xFF),
                    Color.argb((0.70f * 255f + 0.5f).toInt(), 0xFF, 0xFF, 0xFF),
                    Color.argb(255, 0xFF, 0xFF, 0xFF),
                    Color.argb(255, 0xFF, 0xFF, 0xFF)
                )
            } else if (!isDark) {
                intArrayOf(
                    Color.argb(0, 0xFF, 0xFF, 0xFF),
                    Color.argb((0.70f * 255f + 0.5f).toInt(), 0xFF, 0xFF, 0xFF),
                    Color.argb(255, 0xFF, 0xFF, 0xFF),
                    Color.argb(255, 0xFF, 0xFF, 0xFF)
                )
            } else if (isTop) {
                intArrayOf(
                    Color.argb(0, 0x33, 0x6B, 0x85),
                    Color.argb((0.70f * 255f + 0.5f).toInt(), 0x0C, 0x19, 0x1F),
                    Color.argb(255, 0x0C, 0x19, 0x1F),
                    Color.argb(255, 0x0C, 0x19, 0x1F)
                )
            } else {
                intArrayOf(
                    Color.argb(0, 0x33, 0x6B, 0x85),
                    Color.argb((0.70f * 255f + 0.5f).toInt(), 0x0C, 0x19, 0x1F),
                    Color.argb(255, 0x0C, 0x19, 0x1F),
                    Color.argb(255, 0x0C, 0x19, 0x1F)
                )
            }
            val positions = floatArrayOf(0f, 0.30f, 0.61f, 0.94f)

            paint.shader = LinearGradient(
                0f,
                if (isTop) height else 0f,
                0f,
                if (isTop) 0f else height,
                colors,
                positions,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(bounds, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun createBadgeText(game: AppEntry): TextView? {
        val pill = getPillConfig(game) ?: return null
        return TextView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = createBadgeDrawable(pill.borderColor)
            includeFontPadding = false
            text = pill.text
            setTextColor(pill.textColor)
            textSize = 12f
        }
    }

    private fun createLogoOrNameView(game: AppEntry): View {
        val logoUrl = game.logoUrl.trim()
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(232),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )

        return if (logoUrl.isNotEmpty()) {
            ImageView(requireContext()).apply {
                layoutParams = params
                contentDescription = null
                scaleType = ImageView.ScaleType.FIT_CENTER
                ImageLoader.load(logoUrl, this)
            }
        } else {
            TextView(requireContext()).apply {
                layoutParams = params
                gravity = Gravity.CENTER
                text = game.name
                setTextColor(if (isDarkTheme()) Color.WHITE else Color.parseColor("#1F2933"))
                textSize = 34f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }
    }

    private fun getPillConfig(game: AppEntry): PillConfig? {
        val pill = game.pill ?: return null
        val text = pill["text"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val textColor = pill["textColor"]?.toString()?.toColorIntOrNull()
            ?: pill["color"]?.toString()?.toColorIntOrNull()
            ?: return null
        val borderColor = pill["borderColor"]?.toString()?.toColorIntOrNull()
            ?: pill["color"]?.toString()?.toColorIntOrNull()
            ?: return null
        return PillConfig(text = text, textColor = textColor, borderColor = borderColor)
    }

    private fun createMissingBgDrawable(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
//            intArrayOf(
//                Color.parseColor("#"),
//                Color.parseColor("#CED2D5")
//            )
            if (isDarkTheme()) {
                intArrayOf(Color.parseColor("#D7DCE0"), Color.parseColor("#D8F0FA"))
            } else {
                intArrayOf(Color.parseColor("#F4FBFE"), Color.parseColor("#D8F0FA"))
            }
        )
    }

    private fun createSkeletonBgDrawable(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (isDarkTheme()) {
                intArrayOf(Color.parseColor("#C7CED3"), Color.parseColor("#9FA9B0"))
            } else {
                intArrayOf(Color.parseColor("#F4FBFE"), Color.parseColor("#D8F0FA"))
            }
        )

    private fun createSkeletonContentDrawable(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(Color.parseColor(if (isDarkTheme()) "#26000000" else "#66FFFFFF"))
        }

    private fun createSkeletonPillDrawable(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor(if (isDarkTheme()) "#2B3B43" else "#EAF7FC"))
            setStroke(dp(1), Color.parseColor(if (isDarkTheme()) "#45626E" else "#A9D6E8"))
        }

    private fun createSkeletonButtonDrawable(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(Color.parseColor(if (isDarkTheme()) "#4E6A76" else "#BCEAFF"))
        }

    private fun createRoundedCardBackground(
        radius: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidth: Int = 0
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    private fun createBadgeDrawable(borderColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            // Fill: #0C191F at 75% opacity (alpha = 191 = 0xBF)
            setColor(Color.argb(191, 0x0C, 0x19, 0x1F))
            setStroke(dp(1), borderColor)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }

    private fun findTaggedViews(root: View, tag: String): List<View> {
        val matches = mutableListOf<View>()
        fun walk(view: View) {
            if (view.tag == tag) matches.add(view)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
        return matches
    }

    private fun isDarkTheme(): Boolean =
        (props?.currentTheme() ?: GamesHubSession.theme).lowercase() == "dark"

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun String.toColorIntOrNull(): Int? =
        runCatching { Color.parseColor(this) }.getOrNull()

    private fun openGame(game: AppEntry, cardPosition: Int = 0) {
        if (isGameLaunchInProgress) return
        isGameLaunchInProgress = true

        val userId = GamesHubSession.props?.userConfig?.userId?.takeIf { it.isNotBlank() }
            ?: BiomeState.getUserConfig()?.userId?.takeIf { it.isNotBlank() }

        // hub_game_card_tapped: fires immediately on card tap
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

                else -> { /* ignore other actions */ }
            }
        }

        if (!didStartGame) {
            isGameLaunchInProgress = false
        }
    }

    private fun openUrl(url: String, title: String) {
        val intent = Intent(requireContext(), HubWebViewActivity::class.java)
        intent.putExtra("ENTRY_URL", url)
        intent.putExtra("GAME_NAME", title)
        startActivity(intent)
    }

    private data class PillConfig(
        val text: String,
        val textColor: Int,
        val borderColor: Int
    )

    companion object {
        private const val TAG_VIGNETTE_TOP = "game_vignette_top"
        private const val TAG_VIGNETTE_BOTTOM = "game_vignette_bottom"

        fun newInstance(props: BiomeSdkProps? = null): GamesHubFragment {
            val fragment = GamesHubFragment()
            fragment.props = props
            return fragment
        }
    }
}
