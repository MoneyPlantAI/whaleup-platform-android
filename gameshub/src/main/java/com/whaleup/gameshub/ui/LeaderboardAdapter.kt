package com.whaleup.gameshub.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.BiomeState

data class LeaderboardItemData(
    val userId: String,
    val ranking: Int,
    val score: Int,
    val playtime: Int // in seconds
)

class LeaderboardAdapter(
    private var items: List<LeaderboardItemData>
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.llRankItemRoot)
        val tvRankNumber: TextView = view.findViewById(R.id.tvRankNumber)
        val tvAvatarCircle: TextView = view.findViewById(R.id.tvAvatarCircle)
        val tvUserId: TextView = view.findViewById(R.id.tvUserId)
        val tvPlaytime: TextView = view.findViewById(R.id.tvPlaytime)
        val tvScore: TextView = view.findViewById(R.id.tvScore)
        val ivCoinIcon: ImageView = view.findViewById(R.id.ivCoinIcon)
        val tvCrown: TextView = view.findViewById(R.id.tvCrown)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_rank, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val currentUserId = BiomeState.getUserProfile()?.basic?.userId ?: ""
        val isGold = item.ranking == 1
        val isCurrentUser = item.userId.equals(currentUserId, ignoreCase = true) && !isGold
        val density = holder.itemView.context.resources.displayMetrics.density
        val lp = holder.root.layoutParams as ViewGroup.MarginLayoutParams
        if (isGold) {
            lp.topMargin = (12 * density).toInt()
        } else {
            lp.topMargin = 0
        }
        holder.root.layoutParams = lp

        // 1. Row Container Styling
        when {
            isGold -> {
                holder.root.setBackgroundResource(R.drawable.bg_leaderboard_item_gold)
                holder.tvCrown.visibility = View.VISIBLE
                holder.tvUserId.setTextColor(Color.parseColor("#0A3D68"))
                holder.tvPlaytime.setTextColor(Color.parseColor("#A60A3D68"))
                holder.tvScore.setTextColor(Color.parseColor("#0A3D68"))
            }
            isCurrentUser -> {
                holder.root.setBackgroundResource(R.drawable.bg_leaderboard_item_current)
                holder.tvCrown.visibility = View.GONE
                holder.tvUserId.setTextColor(Color.WHITE)
                holder.tvPlaytime.setTextColor(Color.parseColor("#C8FFFFFF"))
                holder.tvScore.setTextColor(Color.WHITE)
            }
            else -> {
                holder.root.setBackgroundResource(R.drawable.bg_leaderboard_item_normal)
                holder.tvCrown.visibility = View.GONE
                holder.tvUserId.setTextColor(Color.parseColor("#0A3D68"))
                holder.tvPlaytime.setTextColor(Color.parseColor("#A60A3D68"))
                holder.tvScore.setTextColor(Color.parseColor("#0A3D68"))
            }
        }

        // 2. Rank Number Circle Badge
        val rankBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            when {
                isGold -> setColor(Color.parseColor("#F5B53A"))
                isCurrentUser -> setColor(Color.parseColor("#40FFFFFF"))
                else -> setColor(Color.parseColor("#1A0A3D68"))
            }
        }
        holder.tvRankNumber.background = rankBg
        holder.tvRankNumber.text = item.ranking.toString()
        holder.tvRankNumber.setTextColor(if (isGold || isCurrentUser) Color.WHITE else Color.parseColor("#0A3D68"))

        // 3. Avatar Profile Circle
        val avatarBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(getAvatarColor(item.ranking)))
            if (isGold) {
                setStroke((2 * density).toInt(), Color.WHITE)
            }
        }
        holder.tvAvatarCircle.background = avatarBg
        val avatarLetter = item.userId.takeIf { it.isNotEmpty() }?.substring(0, 1)?.uppercase() ?: "U"
        holder.tvAvatarCircle.text = avatarLetter
        holder.tvAvatarCircle.setTextColor(Color.parseColor("#0A3D68"))

        // 4. User ID & Playtime
        holder.tvUserId.text = item.userId
        val minutes = item.playtime / 60
        val seconds = item.playtime % 60
        holder.tvPlaytime.text = "${minutes}m ${seconds}s played"

        // 5. Score
        holder.tvScore.text = item.score.toString()
    }

    private fun getAvatarColor(rank: Int): String {
        return when (rank) {
            1 -> "#FFD86B"
            2 -> "#A8D4FF"
            3 -> "#B8EED1"
            else -> "#DFF0FF"
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<LeaderboardItemData>) {
        items = newItems
        notifyDataSetChanged()
    }
}
