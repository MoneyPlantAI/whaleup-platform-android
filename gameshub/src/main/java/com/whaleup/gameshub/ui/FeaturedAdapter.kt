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
    private var isImageGenEnabled: Boolean = false,
    private val onBannerClick: () -> Unit,
    private val onImageGenClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_BANNER = 0
        private const val VIEW_TYPE_IMAGE_GEN = 1
    }

    class BannerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivFeaturedIcon)
    }

    class ImageGenViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        return if (isImageGenEnabled && position == 0) VIEW_TYPE_IMAGE_GEN else VIEW_TYPE_BANNER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_IMAGE_GEN -> {
                val view = inflater.inflate(R.layout.item_image_gen_banner, parent, false)
                ImageGenViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_featured_card, parent, false)
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                BannerViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageGenViewHolder -> {
                holder.itemView.setOnClickListener { onImageGenClick() }
            }
            is BannerViewHolder -> {
                val bannerPosition = if (isImageGenEnabled) position - 1 else position
                if (bannerUrls.isEmpty()) {
                    ImageLoader.loadAsset("herobanner.png", holder.ivIcon)
                } else {
                    ImageLoader.load(bannerUrls[bannerPosition], holder.ivIcon)
                }
                holder.itemView.setOnClickListener { onBannerClick() }
            }
        }
    }

    override fun getItemCount(): Int {
        val apiBannerCount = if (bannerUrls.isEmpty()) 1 else bannerUrls.size
        return if (isImageGenEnabled) apiBannerCount + 1 else apiBannerCount
    }

    fun updateList(newUrls: List<String>, isEnabled: Boolean = isImageGenEnabled) {
        bannerUrls = newUrls
        isImageGenEnabled = isEnabled
        notifyDataSetChanged()
    }
}
