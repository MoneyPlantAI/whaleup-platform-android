package com.whaleup.gameshub.ui

import android.graphics.Color
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
    private val onGameClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<GameCardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivGameIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvGameTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvGameDescription)
        val btnStartPlaying: View = view.findViewById(R.id.btnStartPlaying)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val game = games[position]
        
        holder.tvTitle.text = game.name
        
        val subtitle = if (game.description.isNullOrEmpty()) {
            "Play ${game.name} now!"
        } else {
            game.description
        }
        holder.tvDescription.text = subtitle
        
        bindGameImage(holder, game, position)

        holder.btnStartPlaying.setOnClickListener { onGameClick(game) }
        holder.itemView.setOnClickListener { onGameClick(game) }
    }

    private fun bindGameImage(holder: ViewHolder, game: AppEntry, position: Int) {
        // Reset states
        holder.ivIcon.visibility = View.VISIBLE
        holder.ivIcon.setBackgroundColor(Color.TRANSPARENT)

        when {
            game.bannerImageUrl.isNotEmpty() -> {
                ImageLoader.load(game.bannerImageUrl, holder.ivIcon)
            }
            else -> {
                // Fallback: Theme-based background from skeleton loading
                holder.ivIcon.setImageBitmap(null)
                holder.ivIcon.setBackgroundResource(R.drawable.skeleton_placeholder)
            }
        }
    }

    override fun getItemCount() = games.size

    fun updateList(newGames: List<AppEntry>) {
        games = newGames
        notifyDataSetChanged()
    }

}
