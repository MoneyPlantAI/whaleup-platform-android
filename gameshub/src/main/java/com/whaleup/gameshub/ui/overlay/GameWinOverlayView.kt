package com.whaleup.gameshub.ui.overlay

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.util.ImageLoader

class GameWinOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var flRoot: FrameLayout
    private var btnClose: FrameLayout
    private var btnShare: FrameLayout
    private var btnContinue: FrameLayout
    private var ivRewardImage: ImageView
    private var tvCoinAmount: TextView
    private var tvSubtitle: TextView

    private var currentGameName = "Game"
    private var currentCoinsEarned = 0

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_game_win_overlay, this, true)
        flRoot = view.findViewById(R.id.flGameWinOverlayRoot)
        btnClose = view.findViewById(R.id.btnGameWinClose)
        btnShare = view.findViewById(R.id.btnGameWinShare)
        btnContinue = view.findViewById(R.id.btnGameWinContinue)
        ivRewardImage = view.findViewById(R.id.ivGameWinRewardImage)
        tvCoinAmount = view.findViewById(R.id.tvGameWinCoinAmount)
        tvSubtitle = view.findViewById(R.id.tvGameWinSubtitle)

        btnClose.setOnClickListener { dismiss() }
        btnContinue.setOnClickListener { dismiss() }

        btnShare.setOnClickListener {
            handleShare()
        }
    }

    fun show(gameName: String, coinsEarned: Int) {
        currentGameName = gameName.ifBlank { "game" }
        currentCoinsEarned = Math.max(0, coinsEarned)

        tvCoinAmount.text = currentCoinsEarned.toString()
        tvSubtitle.text = "Yayy, you earned for playing $currentGameName!"

        val bonusConfig = BiomeState.getBonusConfig()
        val treasureUrl = bonusConfig?.images?.treasureBox?.takeIf { it.isNotBlank() }
            ?: "https://raw.githubusercontent.com/MoneyPlantAI/cdn-assets/main/whaleup/images/rewards/treasure-box.png"
        ImageLoader.load(treasureUrl, ivRewardImage)

        bringToFront()
        visibility = View.VISIBLE
        flRoot.visibility = View.VISIBLE
    }

    private fun handleShare() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "I won $currentCoinsEarned coins on WhaleUp!")
                putExtra(Intent.EXTRA_TEXT, "I just won $currentCoinsEarned coins playing $currentGameName on WhaleUp Games! 🎮🏆")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Victory"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismiss() {
        flRoot.visibility = View.GONE
    }
}
