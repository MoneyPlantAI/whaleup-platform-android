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

class FeaturedAdapter(
    private var bannerUrls: List<String>,
    private val onBannerClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_BANNER = 0
    }

    class BannerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivFeaturedIcon)
    }

    override fun getItemViewType(position: Int): Int = VIEW_TYPE_BANNER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_featured_card, parent, false)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return BannerViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is BannerViewHolder) {
            if (bannerUrls.isEmpty()) {
                ImageLoader.loadAsset("herobanner.png", holder.ivIcon)
            } else {
                ImageLoader.load(bannerUrls[position], holder.ivIcon)
            }
            holder.itemView.setOnClickListener { onBannerClick() }
        }
    }

    override fun getItemCount(): Int {
        return if (bannerUrls.isEmpty()) 1 else bannerUrls.size
    }

    fun updateList(newUrls: List<String>) {
        bannerUrls = newUrls
        notifyDataSetChanged()
    }
}
