package com.whaleup.gameshub.ui.overlay

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.whaleup.gameshub.R

data class FTUESlideData(
    val key: String,
    val eyebrow: String,
    val headline: String,
    val body: String,
    val type: FTUEType
)

enum class FTUEType {
    WELCOME,
    BROWSE,
    EARN,
    LEADERBOARD,
    REDEEM
}

class FTUESlideAdapter(
    private val context: Context
) : RecyclerView.Adapter<FTUESlideAdapter.FTUEViewHolder>() {

    private val slides = listOf(
        FTUESlideData(
            key = "welcome",
            eyebrow = "WELCOME",
            headline = "Dive into WhaleUp",
            body = "Casual games, head-to-head matches, and rewards - all inside one tab.",
            type = FTUEType.WELCOME
        ),
        FTUESlideData(
            key = "browse",
            eyebrow = "STEP 1",
            headline = "Pick Your Game",
            body = "Tap any game to jump in. Ludo, Candy Match, Cosmic - fresh games every week.",
            type = FTUEType.BROWSE
        ),
        FTUESlideData(
            key = "earn",
            eyebrow = "STEP 2",
            headline = "Play & Earn Coins",
            body = "Every win drops WhaleUp Coins into your wallet. The better you play, the more you earn.",
            type = FTUEType.EARN
        ),
        FTUESlideData(
            key = "leaderboard",
            eyebrow = "STEP 3",
            headline = "Climb the Leaderboard",
            body = "Compete with players across the country. Top the weekly board for bonus coins.",
            type = FTUEType.LEADERBOARD
        ),
        FTUESlideData(
            key = "redeem",
            eyebrow = "STEP 4",
            headline = "Redeem for Rewards",
            body = "Trade coins for gift cards, mobile recharge, vouchers and in-app perks.",
            type = FTUEType.REDEEM
        )
    )

    override fun getItemCount(): Int = slides.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FTUEViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_ftue_slide, parent, false)
        return FTUEViewHolder(view)
    }

    override fun onBindViewHolder(holder: FTUEViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    class FTUEViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val flIlloContainer: FrameLayout = itemView.findViewById(R.id.flSlideIlloContainer)
        private val tvEyebrow: TextView = itemView.findViewById(R.id.tvSlideEyebrow)
        private val tvHeadline: TextView = itemView.findViewById(R.id.tvSlideHeadline)
        private val tvBody: TextView = itemView.findViewById(R.id.tvSlideBody)

        fun bind(slide: FTUESlideData) {
            tvEyebrow.text = slide.eyebrow
            tvHeadline.text = slide.headline
            tvBody.text = slide.body

            flIlloContainer.removeAllViews()
            val illoView = buildIllustration(itemView.context, slide.type)
            flIlloContainer.addView(illoView)
        }

        private fun buildIllustration(context: Context, type: FTUEType): View {
            return when (type) {
                FTUEType.WELCOME -> buildWelcomeIllo(context)
                FTUEType.BROWSE -> buildBrowseIllo(context)
                FTUEType.EARN -> buildEarnIllo(context)
                FTUEType.LEADERBOARD -> buildLeaderboardIllo(context)
                FTUEType.REDEEM -> buildRedeemIllo(context)
            }
        }

        private fun dpToPx(context: Context, dp: Float): Int {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
        }

        private fun buildWelcomeIllo(context: Context): View {
            val root = FrameLayout(context)

            // Rounded Corner Card for Logo (28dp radius, elevation matching Onboarding.tsx logoFloat)
            val card = CardView(context).apply {
                radius = dpToPx(context, 28f).toFloat()
                cardElevation = dpToPx(context, 10f).toFloat()
                setCardBackgroundColor(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(dpToPx(context, 210f), dpToPx(context, 210f)).apply {
                    gravity = Gravity.CENTER
                }

                val logo = ImageView(context).apply {
                    setImageResource(R.drawable.ic_whaleup_logo)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                addView(logo)
            }
            root.addView(card)

            // Vertical Sinusoidal Float Animation for Logo Card (matching Onboarding.tsx logoFloat)
            ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, 0f, -dpToPx(context, 10f).toFloat(), 0f).apply {
                duration = 5000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                start()
            }

            // Sparkle A (Top-Right, White #FFFFFF, size 24sp, Elevation 16dp ON TOP of image)
            val sparkleA = TextView(context).apply {
                text = "✦"
                textSize = 24f
                setTextColor(Color.WHITE)
                elevation = dpToPx(context, 16f).toFloat()
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                    translationX = dpToPx(context, 105f).toFloat()
                    translationY = dpToPx(context, -95f).toFloat()
                }
            }

            // Sparkle B (Bottom-Left, White #FFFFFF, size 18sp, Elevation 16dp ON TOP of image)
            val sparkleB = TextView(context).apply {
                text = "✦"
                textSize = 18f
                setTextColor(Color.WHITE)
                elevation = dpToPx(context, 16f).toFloat()
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                    translationX = dpToPx(context, -110f).toFloat()
                    translationY = dpToPx(context, 80f).toFloat()
                }
            }

            // Sparkle C (Middle-Right, White #FFFFFF, size 16sp, Elevation 16dp ON TOP of image)
            val sparkleC = TextView(context).apply {
                text = "✦"
                textSize = 16f
                setTextColor(Color.WHITE)
                elevation = dpToPx(context, 16f).toFloat()
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                    translationX = dpToPx(context, 112f).toFloat()
                    translationY = dpToPx(context, -5f).toFloat()
                }
            }

            // Add pulsing floating animation to sparkles
            fun animateSparkle(view: View, delayMs: Long) {
                val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.35f, 1.0f)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.35f, 1.0f)
                val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 1.0f, 0.6f)
                ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY, alpha).apply {
                    duration = 2400
                    startDelay = delayMs
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }

            animateSparkle(sparkleA, 0)
            animateSparkle(sparkleB, 600)
            animateSparkle(sparkleC, 1200)

            root.addView(sparkleA)
            root.addView(sparkleB)
            root.addView(sparkleC)
            return root
        }

        private fun buildBrowseIllo(context: Context): View {
            val root = FrameLayout(context)
            val phone = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dpToPx(context, 26f).toFloat()
                    setStroke(dpToPx(context, 4f), Color.parseColor("#0A3D68"))
                }
                elevation = dpToPx(context, 10f).toFloat()
                layoutParams = FrameLayout.LayoutParams(dpToPx(context, 190f), dpToPx(context, 230f)).apply {
                    gravity = Gravity.CENTER
                }
                setPadding(dpToPx(context, 12f), dpToPx(context, 16f), dpToPx(context, 12f), dpToPx(context, 12f))
            }

            // Phone Header Bar
            val header = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1656C4"))
                    cornerRadius = dpToPx(context, 12f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 60f)).apply {
                    bottomMargin = dpToPx(context, 8f)
                }
            }
            phone.addView(header)

            // Grid of 4 Game Tiles
            val grid1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 55f)).apply {
                    bottomMargin = dpToPx(context, 8f)
                }
            }
            val tile1 = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#3A8DFF"))
                    cornerRadius = dpToPx(context, 12f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = dpToPx(context, 4f)
                }
            }
            val tile2 = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FF9500"))
                    cornerRadius = dpToPx(context, 12f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = dpToPx(context, 4f)
                }
            }
            grid1.addView(tile1)
            grid1.addView(tile2)
            phone.addView(grid1)

            val grid2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 55f))
            }
            val tile3 = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F5D44A"))
                    cornerRadius = dpToPx(context, 12f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = dpToPx(context, 4f)
                }
            }
            val tile4 = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E94AA6"))
                    cornerRadius = dpToPx(context, 12f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = dpToPx(context, 4f)
                }
            }
            grid2.addView(tile3)
            grid2.addView(tile4)
            phone.addView(grid2)

            root.addView(phone)

            // Animated Tap Target over Pink Tile (Tile 4) matching Onboarding.tsx TapTarget
            val tapTargetContainer = FrameLayout(context).apply {
                elevation = dpToPx(context, 16f).toFloat()
                layoutParams = FrameLayout.LayoutParams(dpToPx(context, 48f), dpToPx(context, 48f)).apply {
                    gravity = Gravity.CENTER
                    translationX = dpToPx(context, 44f).toFloat()
                    translationY = dpToPx(context, 48f).toFloat()
                }
            }

            val tapRing = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    shape = GradientDrawable.OVAL
                    setStroke(dpToPx(context, 2.5f), Color.parseColor("#1A6FD8"))
                }
                layoutParams = FrameLayout.LayoutParams(dpToPx(context, 48f), dpToPx(context, 48f))
            }

            val tapTarget = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#401A6FD8"))
                    shape = GradientDrawable.OVAL
                    setStroke(dpToPx(context, 3f), Color.parseColor("#1A6FD8"))
                }
                layoutParams = FrameLayout.LayoutParams(dpToPx(context, 34f), dpToPx(context, 34f)).apply {
                    gravity = Gravity.CENTER
                }
            }

            tapTargetContainer.addView(tapRing)
            tapTargetContainer.addView(tapTarget)
            root.addView(tapTargetContainer)

            // Pulse Animation on Tap Target
            val tapScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 0.85f, 1.0f)
            val tapScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 0.85f, 1.0f)
            ObjectAnimator.ofPropertyValuesHolder(tapTarget, tapScaleX, tapScaleY).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                start()
            }

            // Expanding Ripple Ring Animation
            val ringScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.5f)
            val ringScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.5f)
            val ringAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0.0f)
            ObjectAnimator.ofPropertyValuesHolder(tapRing, ringScaleX, ringScaleY, ringAlpha).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                start()
            }

            return root
        }

        private fun buildEarnIllo(context: Context): View {
            val root = FrameLayout(context)

            val coinShower = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(dpToPx(context, 240f), dpToPx(context, 190f)).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
            }

            fun createOrbitCoin(sizeDp: Float, leftDp: Float?, topDp: Float?, rightDp: Float?, bottomDp: Float?, durationMs: Long, delayMs: Long, rangeDp: Float): ImageView {
                val coin = ImageView(context).apply {
                    setImageResource(R.drawable.ic_coin_reward)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = FrameLayout.LayoutParams(dpToPx(context, sizeDp), dpToPx(context, sizeDp)).apply {
                        val hGrav = if (rightDp != null && leftDp == null) Gravity.END else Gravity.START
                        val vGrav = if (bottomDp != null && topDp == null) Gravity.BOTTOM else Gravity.TOP
                        gravity = hGrav or vGrav

                        leftDp?.let { marginStart = dpToPx(context, it) }
                        topDp?.let { topMargin = dpToPx(context, it) }
                        rightDp?.let { marginEnd = dpToPx(context, it) }
                        bottomDp?.let { bottomMargin = dpToPx(context, it) }
                    }
                }

                ObjectAnimator.ofFloat(coin, View.TRANSLATION_Y, 0f, -dpToPx(context, rangeDp).toFloat(), 0f).apply {
                    duration = durationMs
                    startDelay = delayMs
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    start()
                }

                return coin
            }

            // Coin 1 (Main Center Coin - 120dp: left 60dp, top 40dp)
            val mainCoin = createOrbitCoin(120f, leftDp = 60f, topDp = 40f, rightDp = null, bottomDp = null, durationMs = 3000, delayMs = 0, rangeDp = 8f)
            // Coin 2 (Orbit 1 - 44dp: left 18dp, top 12dp)
            val orbit1 = createOrbitCoin(44f, leftDp = 18f, topDp = 12f, rightDp = null, bottomDp = null, durationMs = 4000, delayMs = 0, rangeDp = 12f)
            // Coin 3 (Orbit 2 - 36dp: right 10dp, top 24dp)
            val orbit2 = createOrbitCoin(36f, leftDp = null, topDp = 24f, rightDp = 10f, bottomDp = null, durationMs = 5000, delayMs = 600, rangeDp = 12f)
            // Coin 4 (Orbit 3 - 52dp: left 0dp, bottom 10dp)
            val orbit3 = createOrbitCoin(52f, leftDp = 0f, topDp = null, rightDp = null, bottomDp = 10f, durationMs = 4500, delayMs = 1200, rangeDp = 12f)
            // Coin 5 (Orbit 4 - 28dp: right 18dp, bottom 36dp)
            val orbit4 = createOrbitCoin(28f, leftDp = null, topDp = null, rightDp = 18f, bottomDp = 36f, durationMs = 3800, delayMs = 400, rangeDp = 12f)
            // Coin 6 (Orbit 5 - 40dp: right 0dp, top 76dp)
            val orbit5 = createOrbitCoin(40f, leftDp = null, topDp = 76f, rightDp = 0f, bottomDp = null, durationMs = 4200, delayMs = 1600, rangeDp = 12f)

            coinShower.addView(mainCoin)
            coinShower.addView(orbit1)
            coinShower.addView(orbit2)
            coinShower.addView(orbit3)
            coinShower.addView(orbit4)
            coinShower.addView(orbit5)

            // Banner Pill: +250 [Coin] WhaleUp Coins (Blue text & blue 3dp border matching Onboarding.tsx earnBanner)
            val bannerCard = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dpToPx(context, 999f).toFloat()
                    setStroke(dpToPx(context, 3f), Color.parseColor("#1A6FD8"))
                }
                elevation = dpToPx(context, 5f).toFloat()
                setPadding(dpToPx(context, 18f), dpToPx(context, 8f), dpToPx(context, 18f), dpToPx(context, 8f))
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dpToPx(context, 24f)
                }
            }

            val txtPlus = TextView(context).apply {
                text = "+250"
                textSize = 18f
                setTextColor(Color.parseColor("#1A6FD8"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val iconCoin = ImageView(context).apply {
                setImageResource(R.drawable.ic_coin_reward)
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 28f), dpToPx(context, 28f)).apply {
                    marginStart = dpToPx(context, 8f)
                    marginEnd = dpToPx(context, 8f)
                }
            }
            val txtCoins = TextView(context).apply {
                text = "WhaleUp Coins"
                textSize = 18f
                setTextColor(Color.parseColor("#1A6FD8"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            bannerCard.addView(txtPlus)
            bannerCard.addView(iconCoin)
            bannerCard.addView(txtCoins)

            root.addView(coinShower)
            root.addView(bannerCard)
            return root
        }

        private fun buildLeaderboardIllo(context: Context): View {
            val root = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
            }

            val lbContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(dpToPx(context, 280f), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
            }

            fun createRow(rank: String, avatarText: String, name: String, coins: String, avatarBgColor: Int, isGold: Boolean = false, isYou: Boolean = false): View {
                val rowWrapper = FrameLayout(context).apply {
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dpToPx(context, 8f)
                    }
                }

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dpToPx(context, 56f)
                    background = GradientDrawable().apply {
                        setColor(if (isYou) Color.parseColor("#1A6FD8") else if (isGold) Color.parseColor("#FFE27A") else Color.parseColor("#D9FFFFFF"))
                        cornerRadius = dpToPx(context, 14f).toFloat()
                        setStroke(
                            dpToPx(context, 1.5f),
                            if (isYou) Color.TRANSPARENT else if (isGold) Color.parseColor("#F5B53A") else Color.parseColor("#2E1A6FD8")
                        )
                    }
                    elevation = dpToPx(context, if (isGold) 6f else 3f).toFloat()
                    setPadding(dpToPx(context, 14f), dpToPx(context, 8f), dpToPx(context, 14f), dpToPx(context, 8f))
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 56f))
                }

                // Rank Circle Badge (28dp x 28dp)
                val rankBadge = TextView(context).apply {
                    text = rank
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(if (isGold || isYou) Color.WHITE else Color.parseColor("#0A3D68"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        setColor(if (isGold) Color.parseColor("#F5B53A") else if (isYou) Color.parseColor("#40FFFFFF") else Color.parseColor("#1A0A3D68"))
                        shape = GradientDrawable.OVAL
                    }
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 28f), dpToPx(context, 28f)).apply {
                        marginEnd = dpToPx(context, 10f)
                    }
                }

                // Avatar Circle Badge (36dp x 36dp)
                val avatarBadge = TextView(context).apply {
                    text = avatarText
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#0A3D68"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        setColor(avatarBgColor)
                        shape = GradientDrawable.OVAL
                        if (isGold) {
                            setStroke(dpToPx(context, 2f), Color.WHITE)
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 36f), dpToPx(context, 36f)).apply {
                        marginEnd = dpToPx(context, 10f)
                    }
                }

                // Player Name
                val txtName = TextView(context).apply {
                    text = name
                    textSize = 15f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(if (isYou) Color.WHITE else Color.parseColor("#0A3D68"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                // Coins Container (Icon + Text)
                val coinWrap = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val coinIcon = ImageView(context).apply {
                    setImageResource(R.drawable.ic_coin_reward)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 18f), dpToPx(context, 18f)).apply {
                        marginEnd = dpToPx(context, 4f)
                    }
                }

                val txtCoins = TextView(context).apply {
                    text = coins
                    textSize = 14f
                    setTextColor(if (isYou) Color.WHITE else Color.parseColor("#0A3D68"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                coinWrap.addView(coinIcon)
                coinWrap.addView(txtCoins)

                row.addView(rankBadge)
                row.addView(avatarBadge)
                row.addView(txtName)
                row.addView(coinWrap)

                rowWrapper.addView(row)

                // Gold Crown 👑 on top right rotated 15 deg (Elevated ON TOP of gold card)
                if (isGold) {
                    val crown = TextView(context).apply {
                        text = "👑"
                        textSize = 28f
                        rotation = 15f
                        elevation = dpToPx(context, 16f).toFloat()
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                            gravity = Gravity.TOP or Gravity.END
                            marginEnd = dpToPx(context, 10f)
                            topMargin = dpToPx(context, -16f)
                        }
                    }
                    rowWrapper.addView(crown)
                }

                return rowWrapper
            }

            // Row 1: Priya (Rank 2)
            lbContainer.addView(createRow("2", "P", "Priya", "4,820", Color.parseColor("#A8D4FF")))
            // Row 2: Rohan (Rank 1 Gold 👑)
            lbContainer.addView(createRow("1", "R", "Rohan", "6,140", Color.parseColor("#FFD86B"), isGold = true))
            // Row 3: Aanya (Rank 3)
            lbContainer.addView(createRow("3", "A", "Aanya", "3,510", Color.parseColor("#B8EED1")))
            // Row 4: You (Rank 12 You)
            lbContainer.addView(createRow("12", "You", "You", "1,180", Color.WHITE, isYou = true))

            root.addView(lbContainer)
            return root
        }

        private fun buildRedeemIllo(context: Context): View {
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }

            val grid = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 240f), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            fun createTile(iconEmoji: String, titleText: String, bgTint: Int): View {
                return LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#D9FFFFFF"))
                        cornerRadius = dpToPx(context, 16f).toFloat()
                        setStroke(dpToPx(context, 1.5f), Color.parseColor("#2E1A6FD8"))
                    }
                    elevation = dpToPx(context, 4f).toFloat()
                    setPadding(dpToPx(context, 8f), dpToPx(context, 14f), dpToPx(context, 8f), dpToPx(context, 14f))
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 114f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dpToPx(context, 3f)
                        marginStart = dpToPx(context, 3f)
                    }

                    val iconCircle = FrameLayout(context).apply {
                        background = GradientDrawable().apply {
                            setColor(bgTint)
                            cornerRadius = dpToPx(context, 12f).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(dpToPx(context, 44f), dpToPx(context, 44f)).apply {
                            bottomMargin = dpToPx(context, 6f)
                        }
                        val emoji = TextView(context).apply {
                            text = iconEmoji
                            textSize = 22f
                            gravity = Gravity.CENTER
                            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        }
                        addView(emoji)
                    }

                    val label = TextView(context).apply {
                        text = titleText
                        textSize = 13f
                        setTextColor(Color.parseColor("#0A3D68"))
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                    }

                    addView(iconCircle)
                    addView(label)
                }
            }

            val row1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(context, 12f)
                }
            }
            row1.addView(createTile("🎁", "Gift Cards", Color.parseColor("#FFF5D6")))
            row1.addView(createTile("📱", "Recharge", Color.parseColor("#DFF0FF")))

            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            row2.addView(createTile("🛍️", "Vouchers", Color.parseColor("#FFE0E6")))
            row2.addView(createTile("💎", "In-app Perks", Color.parseColor("#E8E0FF")))

            grid.addView(row1)
            grid.addView(row2)

            // Arrow & Gift Box Row below Grid (Matching Onboarding.tsx redeemArrow)
            val redeemArrow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpToPx(context, 14f)
                }

                // 1. Large Coin (56dp x 56dp)
                val coin = ImageView(context).apply {
                    setImageResource(R.drawable.ic_coin_reward)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 56f), dpToPx(context, 56f))
                }

                // 2. Arrow Line + Arrow Head Wrap (60dp wide x 20dp high)
                val arrowWrap = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 60f), dpToPx(context, 20f)).apply {
                        marginStart = dpToPx(context, 8f)
                        marginEnd = dpToPx(context, 8f)
                    }

                    // Main horizontal line (50dp long x 3dp high, accent blue #1A6FD8)
                    val arrowLine = View(context).apply {
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1A6FD8"))
                            cornerRadius = dpToPx(context, 2f).toFloat()
                        }
                        layoutParams = FrameLayout.LayoutParams(dpToPx(context, 50f), dpToPx(context, 3f)).apply {
                            gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        }
                    }

                    // Arrow Top Wing (rotated 35 deg)
                    val arrowHeadTop = View(context).apply {
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1A6FD8"))
                            cornerRadius = dpToPx(context, 2f).toFloat()
                        }
                        rotation = 35f
                        layoutParams = FrameLayout.LayoutParams(dpToPx(context, 14f), dpToPx(context, 3f)).apply {
                            gravity = Gravity.TOP or Gravity.END
                            marginEnd = dpToPx(context, 8f)
                            topMargin = dpToPx(context, 3f)
                        }
                    }

                    // Arrow Bottom Wing (rotated -35 deg)
                    val arrowHeadBottom = View(context).apply {
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1A6FD8"))
                            cornerRadius = dpToPx(context, 2f).toFloat()
                        }
                        rotation = -35f
                        layoutParams = FrameLayout.LayoutParams(dpToPx(context, 14f), dpToPx(context, 3f)).apply {
                            gravity = Gravity.BOTTOM or Gravity.END
                            marginEnd = dpToPx(context, 8f)
                            bottomMargin = dpToPx(context, 3f)
                        }
                    }

                    addView(arrowLine)
                    addView(arrowHeadTop)
                    addView(arrowHeadBottom)
                }

                // 3. Redeem Gift Card (52dp x 52dp, background #FFF5D6, 3dp border #F5B53A, emoji 🎁)
                val redeemGift = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#FFF5D6"))
                        cornerRadius = dpToPx(context, 16f).toFloat()
                        setStroke(dpToPx(context, 3f), Color.parseColor("#F5B53A"))
                    }
                    elevation = dpToPx(context, 4f).toFloat()
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 52f), dpToPx(context, 52f))

                    val giftEmoji = TextView(context).apply {
                        text = "🎁"
                        textSize = 24f
                        gravity = Gravity.CENTER
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    }
                    addView(giftEmoji)
                }

                addView(coin)
                addView(arrowWrap)
                addView(redeemGift)
            }

            root.addView(grid)
            root.addView(redeemArrow)
            return root
        }
    }
}
