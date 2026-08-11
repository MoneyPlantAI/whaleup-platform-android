package com.whaleup.gameshub.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.AppEntry
import com.whaleup.gameshub.util.ImageLoader

class GameCardAdapter(
    private var games: List<AppEntry>,
    private val onGameClick: (AppEntry, Int) -> Unit
) : RecyclerView.Adapter<GameCardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.gameCardRoot)
        val ivBanner: ImageView = view.findViewById(R.id.ivGameBanner)
        val tvFallbackName: TextView = view.findViewById(R.id.tvFallbackName)
        val tvNewBadge: TextView = view.findViewById(R.id.tvNewBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_card, parent, false)

        val density = parent.context.resources.displayMetrics.density
        val parentWidth = parent.measuredWidth.takeIf { it > 0 }
            ?: parent.context.resources.displayMetrics.widthPixels
        
        // Equal spacing between row items and column items (12dp total inter-card gap)
        val marginDp = 6
        val paddingHorizontalDp = 12
        val totalSpacingDp = (paddingHorizontalDp * 2) + (marginDp * 4) // 48dp total inset space
        val paddingTotal = (totalSpacingDp * density).toInt()
        val cardSize = (parentWidth - paddingTotal) / 2
        val margin = (marginDp * density).toInt()

        val lp = RecyclerView.LayoutParams(cardSize, (cardSize * 1.01).toInt()).apply {
            setMargins(margin, margin, margin, margin)
        }
        view.layoutParams = lp
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val game = games[position]

        val iconUrl = game.bannerImageUrl.ifEmpty { game.logoUrl.ifEmpty { game.bgUrl } }

        holder.tvFallbackName.text = game.name
        if (iconUrl.isNotEmpty()) {
            holder.ivBanner.visibility = View.VISIBLE
            holder.tvFallbackName.visibility = View.GONE
            ImageLoader.load(iconUrl, holder.ivBanner)
        } else {
            holder.ivBanner.visibility = View.GONE
            holder.tvFallbackName.visibility = View.VISIBLE
        }

        holder.tvNewBadge.visibility = View.GONE

        holder.root.setOnClickListener { onGameClick(game, position) }
    }

    override fun getItemCount() = games.size

    fun updateList(newGames: List<AppEntry>) {
        games = newGames
        notifyDataSetChanged()
    }
}
