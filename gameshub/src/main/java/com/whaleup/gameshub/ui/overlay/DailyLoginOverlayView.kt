package com.whaleup.gameshub.ui.overlay

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.network.APIBridge
import com.whaleup.gameshub.network.APICallback
import com.whaleup.gameshub.util.ImageLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyLoginOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("whaleup_player_prefs", Context.MODE_PRIVATE)

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
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastLoginDate = prefs.getString("last_login_date", "")
        val isCompleted = prefs.getBoolean("daily_login_completed", false)

        if (today == lastLoginDate && isCompleted) {
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
        tvTitle.text = "You earned"
        tvCtaText.text = "Play & earn"
        tvCtaArrow.visibility = View.VISIBLE

        // Load treasure box image (with CDN fallback)
        val treasureUrl = bonusConfig?.images?.treasureBox?.takeIf { it.isNotBlank() }
            ?: "https://raw.githubusercontent.com/MoneyPlantAI/cdn-assets/main/whaleup/images/rewards/treasure-box.png"
        ImageLoader.load(treasureUrl, ivTreasure)

        val supercoinUrl = bonusConfig?.images?.supercoinIcon
        if (!supercoinUrl.isNullOrBlank()) {
            ImageLoader.load(supercoinUrl, ivCoinIcon)
        }

        bringToFront()
        visibility = View.VISIBLE
        flRoot.visibility = View.VISIBLE
        return true
    }

    private fun handleClaim() {
        if (isClaiming) return
        isClaiming = true
        btnClaim.alpha = 0.65f
        tvCtaText.text = "Claiming..."
        tvCtaArrow.visibility = View.GONE
        tvTitle.text = "Claiming..."

        val userId = BiomeState.getUserProfile()?.basic?.userId ?: ""
        APIBridge.claimGullak(userId, object : APICallback {
            override fun onSuccess(response: String) {
                saveClaimCompleted()
                post {
                    isClaiming = false
                    btnClaim.alpha = 1.0f
                    dismiss()
                }
            }

            override fun onError(code: Int, message: String) {
                // Save locally on completion
                saveClaimCompleted()
                post {
                    isClaiming = false
                    btnClaim.alpha = 1.0f
                    dismiss()
                }
            }
        })
    }

    private fun saveClaimCompleted() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        prefs.edit()
            .putBoolean("daily_login_completed", true)
            .putString("last_login_date", today)
            .apply()
    }

    fun dismiss() {
        flRoot.visibility = View.GONE
    }
}
