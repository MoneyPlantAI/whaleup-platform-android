package com.whaleup.gameshub.ui.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.data.HubSessionFlow
import com.whaleup.gameshub.data.BiomeMessageType
import com.whaleup.gameshub.data.SDKEvent
import com.whaleup.gameshub.data.PlayerPrefsManager
import com.whaleup.gameshub.data.SessionEligibility
import com.whaleup.gameshub.network.APIBridge
import com.whaleup.gameshub.network.APICallback
import com.whaleup.gameshub.messaging.toMap
import com.whaleup.gameshub.ui.HubStrings
import com.whaleup.gameshub.util.ImageLoader
import org.json.JSONObject

class DailyLoginOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var flRoot: FrameLayout
    private var btnClose: FrameLayout
    private var btnClaim: FrameLayout
    private var ivTreasure: ImageView
    private var ivCoinIcon: ImageView
    private var tvDayText: TextView
    private var tvTitle: TextView
    private var tvCoinAmount: TextView
    private var tvCtaText: TextView
    private var tvCtaArrow: TextView

    private var isClaiming = false
    private var loginCoins = 0
    private var displayDay = 1
    private var viewedDay: Int? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_daily_login_overlay, this, true)
        flRoot = view.findViewById(R.id.flDailyLoginOverlayRoot)
        btnClose = view.findViewById(R.id.btnDailyLoginClose)
        btnClaim = view.findViewById(R.id.btnDailyLoginClaim)
        ivTreasure = view.findViewById(R.id.ivDailyLoginTreasure)
        ivCoinIcon = view.findViewById(R.id.ivDailyLoginCoinIcon)
        tvDayText = view.findViewById(R.id.tvDailyLoginDayText)
        tvTitle = view.findViewById(R.id.tvDailyLoginTitle)
        tvCoinAmount = view.findViewById(R.id.tvDailyLoginCoinAmount)
        tvCtaText = view.findViewById(R.id.tvDailyLoginCtaText)
        tvCtaArrow = view.findViewById(R.id.tvDailyLoginCtaArrow)

        btnClose.setOnClickListener {
            if (!isClaiming) dismiss()
        }

        btnClaim.setOnClickListener {
            handleClaim()
        }
    }

    fun showIfEligible(): Boolean {
        if (SessionEligibility.currentFlow() != HubSessionFlow.DAILY_LOGIN) {
            return false
        }

        val profile = BiomeState.getUserProfile()
        val bonusConfig = BiomeState.getBonusConfig()
        val loginDay = profile?.login?.loginDay ?: 1
        displayDay = Math.max(1, loginDay)

        val journeyDays = bonusConfig?.rewardsJourneyDays ?: 7
        val dayConfig = bonusConfig?.coinBonus?.find { it.loginDay == loginDay }
        loginCoins = if (loginDay > journeyDays) {
            bonusConfig?.afterJourneyDailyRewardCoins ?: 0
        } else {
            dayConfig?.loginBonus ?: 0
        }

        // Set UI values matching RN DailyLoginOverlay.tsx
        tvDayText.text = "Day $displayDay"
        tvCoinAmount.text = loginCoins.toString()
        tvTitle.text = HubStrings.get("dailyLogin.youEarned", "You earned").trim()
        tvCtaText.text = HubStrings.get("dailyLogin.ctaButton", "Play & earn")
        tvCtaArrow.visibility = View.VISIBLE

        // Load treasure box image (with CDN fallback)
        ivTreasure.setImageResource(R.drawable.ic_coin_reward)
        val treasureUrl = BiomeState.getImageConfig()?.treasureBox?.takeIf { it.isNotBlank() }
        if (treasureUrl != null) {
            ImageLoader.load(treasureUrl, ivTreasure, preserveExistingImage = true)
        }

        val supercoinUrl = bonusConfig?.images?.supercoinIcon
        if (!supercoinUrl.isNullOrBlank()) {
            ImageLoader.load(supercoinUrl, ivCoinIcon)
        }

        bringToFront()
        visibility = View.VISIBLE
        flRoot.visibility = View.VISIBLE
        if (viewedDay != displayDay) {
            viewedDay = displayDay
            emitAnalytics(
                action = "daily_login_coin_earned_popup_viewed",
                message = "Daily login coin earned popup viewed",
                data = mapOf(
                    "sesson_id" to (BiomeState.getSessionId() ?: ""),
                    "user_id" to (profile?.basic?.userId ?: ""),
                    "coin_amount" to loginCoins
                )
            )
        }
        return true
    }

    private fun handleClaim() {
        if (isClaiming) return
        isClaiming = true
        emitAnalytics(
            action = "Daily_Login_Claimed",
            message = "Daily login reward claimed",
            data = mapOf("loginReward" to loginCoins)
        )
        btnClaim.alpha = 0.65f
        tvCtaText.text = HubStrings.get("dailyLogin.claiming", "Claiming…")
        tvCtaArrow.visibility = View.GONE
        tvTitle.text = HubStrings.get("dailyLogin.claiming", "Claiming…")

        val userId = BiomeState.getUserProfile()?.basic?.userId?.takeIf { it.isNotBlank() }
            ?: GamesHubSession.props?.userConfig?.userId?.takeIf { it.isNotBlank() }
            ?: BiomeState.getUserConfig()?.userId?.takeIf { it.isNotBlank() }

        if (userId == null) {
            restoreClaimButton()
            return
        }

        APIBridge.claimGullak(userId, object : APICallback {
            override fun onSuccess(response: String) {
                saveClaimCompleted()
                refreshProfileAfterClaim(userId)
            }

            override fun onError(code: Int, message: String) {
                post { restoreClaimButton() }
            }
        })
    }

    private fun refreshProfileAfterClaim(userId: String) {
        APIBridge.getUserProfile(
            mapOf(
                "userId" to userId,
                "returns" to "basic,login,gameStats,earnings,signUp,claimableRewards"
            ),
            object : APICallback {
                override fun onSuccess(response: String) {
                    runCatching {
                        BiomeState.setUserProfile(JSONObject(response).toMap(), "server")
                    }
                    finishSuccessfulClaim()
                }

                override fun onError(code: Int, message: String) {
                    finishSuccessfulClaim()
                }
            }
        )
    }

    private fun finishSuccessfulClaim() {
        post {
            isClaiming = false
            btnClaim.alpha = 1.0f
            dismiss()
        }
    }

    private fun restoreClaimButton() {
        isClaiming = false
        btnClaim.alpha = 1.0f
        tvTitle.text = HubStrings.get("dailyLogin.youEarned", "You earned").trim()
        tvCtaText.text = HubStrings.get("dailyLogin.ctaButton", "Play & earn")
        tvCtaArrow.visibility = View.VISIBLE
    }

    private fun saveClaimCompleted() {
        PlayerPrefsManager.set("daily_login_completed", true)
        PlayerPrefsManager.set("last_login_date", SessionEligibility.today())
    }

    private fun emitAnalytics(action: String, message: String, data: Map<String, Any?>) {
        GamesHubSession.props?.onWhaleupSDKEvent?.invoke(
            SDKEvent(type = BiomeMessageType.ANALYTICS_EVENT, action = action, message = message, data = data)
        )
    }

    fun dismiss() {
        flRoot.visibility = View.GONE
    }
}
