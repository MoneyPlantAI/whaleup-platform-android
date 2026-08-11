package com.whaleup.gameshub.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.whaleup.gameshub.R
import com.whaleup.gameshub.util.ImageLoader

class BannerAdapter(
    private var imageUrls: List<String>,
    private val onBannerClick: (String, Int) -> Unit
) : RecyclerView.Adapter<BannerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivBanner: ImageView = view.findViewById(R.id.ivBannerImage)
        val flSkeleton: View = view.findViewById(R.id.flBannerSkeleton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_banner_slide, parent, false)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = imageUrls[position]
        holder.flSkeleton.visibility = View.VISIBLE

        ImageLoader.load(url, holder.ivBanner) { success ->
            holder.flSkeleton.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onBannerClick(url, position)
        }
    }

    override fun getItemCount(): Int = imageUrls.size

    fun updateUrls(newUrls: List<String>) {
        imageUrls = newUrls
        notifyDataSetChanged()
    }
}
