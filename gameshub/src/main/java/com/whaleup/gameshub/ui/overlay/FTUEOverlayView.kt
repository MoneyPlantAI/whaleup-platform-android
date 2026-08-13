package com.whaleup.gameshub.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.data.BiomeMessageType
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.data.HubSessionFlow
import com.whaleup.gameshub.data.PlayerPrefsManager
import com.whaleup.gameshub.data.SDKEvent
import com.whaleup.gameshub.data.SessionEligibility
import com.whaleup.gameshub.ui.HubStrings

class FTUEOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onComplete: (() -> Unit)? = null

    private var flRoot: FrameLayout
    private var flTopBar: FrameLayout
    private var flBottomBar: FrameLayout
    private var llPagerDots: LinearLayout
    private var btnSkip: TextView
    private var vpPager: ViewPager2
    private var vCtaBase: View
    private var btnCtaFace: FrameLayout
    private var tvCtaText: TextView
    private var tvCtaArrow: TextView

    private val adapter = FTUESlideAdapter(context)
    private var activeIndex = 0

    companion object {
        private const val PREF_KEY_FTUE_COMPLETED = "ftue_completed"
    }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ftue_overlay, this, true)
        flRoot = view.findViewById(R.id.flFtueOverlayRoot)
        flTopBar = view.findViewById(R.id.flFtueTopBar)
        flBottomBar = view.findViewById(R.id.flFtueBottomBar)
        llPagerDots = view.findViewById(R.id.llFtuePagerDots)
        btnSkip = view.findViewById(R.id.btnFtueSkip)
        vpPager = view.findViewById(R.id.vpFtuePager)
        vCtaBase = view.findViewById(R.id.vFtueCtaBase)
        btnCtaFace = view.findViewById(R.id.btnFtueCtaFace)
        tvCtaText = view.findViewById(R.id.tvFtueCtaText)
        tvCtaArrow = view.findViewById(R.id.tvFtueCtaArrow)

        vpPager.adapter = adapter

        // Setup WindowInsets for Safe Area (Top Status Bar & Bottom Navigation Bar)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            flTopBar.setPadding(
                flTopBar.paddingLeft,
                statusBarHeight + dpToPx(12f),
                flTopBar.paddingRight,
                flTopBar.paddingBottom
            )

            flBottomBar.setPadding(
                flBottomBar.paddingLeft,
                flBottomBar.paddingTop,
                flBottomBar.paddingRight,
                navBarHeight + dpToPx(24f)
            )

            insets
        }

        setupDotsIndicator()

        vpPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                activeIndex = position
                updateDotsIndicator(position)
                val isLast = position == adapter.itemCount - 1
                if (isLast) {
                    vCtaBase.setBackgroundResource(R.drawable.bg_ftue_cta_base_final)
                    btnCtaFace.setBackgroundResource(R.drawable.bg_ftue_cta_face_final)
                    tvCtaText.text = HubStrings.get("onboarding.startPlaying", "Start Playing")
                    tvCtaText.setTextColor(Color.parseColor("#6A3A05"))
                    tvCtaArrow.setTextColor(Color.parseColor("#6A3A05"))
                    btnSkip.visibility = View.INVISIBLE
                } else {
                    vCtaBase.setBackgroundResource(R.drawable.bg_ftue_cta_base)
                    btnCtaFace.setBackgroundResource(R.drawable.bg_ftue_cta_face)
                    tvCtaText.text = HubStrings.get("onboarding.next", "Next")
                    tvCtaText.setTextColor(Color.WHITE)
                    tvCtaArrow.setTextColor(Color.WHITE)
                    btnSkip.visibility = View.VISIBLE
                }
            }
        })

        btnSkip.setOnClickListener {
            completeFTUE()
        }

        // 3D Tactile Up-Down Press Effect
        btnCtaFace.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btnCtaFace.animate().translationY(dpToPx(4f).toFloat()).setDuration(50).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    btnCtaFace.animate().translationY(0f).setDuration(50).start()
                }
            }
            false
        }

        btnCtaFace.setOnClickListener {
            if (activeIndex < adapter.itemCount - 1) {
                vpPager.setCurrentItem(activeIndex + 1, true)
            } else {
                completeFTUE()
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }

    private fun setupDotsIndicator() {
        llPagerDots.removeAllViews()
        for (i in 0 until adapter.itemCount) {
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(6f), dpToPx(6f)).apply {
                    setMargins(dpToPx(3f), 0, dpToPx(3f), 0)
                }
            }
            llPagerDots.addView(dot)
        }
        updateDotsIndicator(0)
    }

    private fun updateDotsIndicator(activePos: Int) {
        for (i in 0 until llPagerDots.childCount) {
            val dot = llPagerDots.getChildAt(i)
            val isActive = i == activePos
            val width = if (isActive) dpToPx(20f) else dpToPx(6f)
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                this.width = width
            }
            dot.background = GradientDrawable().apply {
                setColor(if (isActive) Color.parseColor("#1A6FD8") else Color.parseColor("#80FFFFFF"))
                cornerRadius = dpToPx(3f).toFloat()
            }
        }
    }

    fun showIfEligible(): Boolean {
        if (SessionEligibility.currentFlow() == HubSessionFlow.FTUE) {
            show()
            return true
        }
        return false
    }

    fun show() {
        vpPager.setCurrentItem(0, false)
        activeIndex = 0
        updateDotsIndicator(0)
        vCtaBase.setBackgroundResource(R.drawable.bg_ftue_cta_base)
        btnCtaFace.setBackgroundResource(R.drawable.bg_ftue_cta_face)
        tvCtaText.text = HubStrings.get("onboarding.next", "Next")
        btnSkip.text = HubStrings.get("onboarding.skip", "Skip")
        btnSkip.visibility = View.VISIBLE
        bringToFront()
        visibility = View.VISIBLE
        flRoot.visibility = View.VISIBLE
    }

    private fun completeFTUE() {
        PlayerPrefsManager.set(PREF_KEY_FTUE_COMPLETED, true)
        PlayerPrefsManager.set("is_first_visit", false)
        BiomeState.setFtueCompleted(true)

        // Fire analytics event
        val userId = GamesHubSession.props?.userConfig?.userId?.takeIf { it.isNotBlank() }
            ?: BiomeState.getUserConfig()?.userId
        GamesHubSession.props?.onWhaleupSDKEvent?.invoke(
            SDKEvent(
                type = BiomeMessageType.ANALYTICS_EVENT,
                action = "learn_how_to_closed",
                message = "Learn how to closed",
                data = buildMap {
                    put("user_id", userId ?: "")
                }
            )
        )

        dismiss()
        onComplete?.invoke()
    }

    fun dismiss() {
        flRoot.visibility = View.GONE
        visibility = View.GONE
    }
}
