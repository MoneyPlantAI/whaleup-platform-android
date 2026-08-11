package com.whaleup.gameshub.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.whaleup.gameshub.R

class CategoryChipAdapter(
    private var categories: List<String>,
    private val onCategoryClick: (String, Boolean) -> Unit
) : RecyclerView.Adapter<CategoryChipAdapter.ViewHolder>() {

    private var selectedCategory: String? = null
    
    private var chipSelectedColor: Int = 0
    private var chipUnselectedColor: Int = 0
    private var dividerColor: Int = 0
    private var chipSelectedTextColor: Int = 0
    private var chipUnselectedTextColor: Int = 0

    init {
        // Resolve colors once if possible, but context is needed. 
        // We'll resolve in onCreateViewHolder or onBindViewHolder.
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvCategoryName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_chip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.tvName.text = category

        val isSelected = category == selectedCategory
        
        if (chipSelectedColor == 0) {
            val typedArray = holder.itemView.context.obtainStyledAttributes(
                intArrayOf(
                    R.attr.ghChipSelectedColor,
                    R.attr.ghChipUnselectedColor,
                    R.attr.ghChipSelectedTextColor,
                    R.attr.ghChipUnselectedTextColor,
                    R.attr.ghDividerColor
                )
            )
            chipSelectedColor = typedArray.getColor(0, Color.BLUE)
            chipUnselectedColor = typedArray.getColor(1, Color.LTGRAY)
            chipSelectedTextColor = typedArray.getColor(2, Color.WHITE)
            chipUnselectedTextColor = typedArray.getColor(3, Color.BLACK)
            dividerColor = typedArray.getColor(4, Color.LTGRAY)
            typedArray.recycle()
        }

        // Manual background tinting since we're avoiding XML state lists for simplicity in this demo logic
        val bg = holder.tvName.background as GradientDrawable
        
        if (isSelected) {
            bg.setColor(chipSelectedColor)
            bg.setStroke(2, chipSelectedColor) // Thicker blue border when selected
            holder.tvName.setTextColor(chipSelectedTextColor)
        } else {
            bg.setColor(chipUnselectedColor)
            bg.setStroke(2, dividerColor)
            holder.tvName.setTextColor(chipUnselectedTextColor)
        }

        holder.itemView.setOnClickListener {
            if (selectedCategory == category) {
                // Deselect -> Fallback to "All"
                selectedCategory = "All"
                onCategoryClick("All", true)
            } else {
                // Select
                selectedCategory = category
                onCategoryClick(category, true)
            }
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = categories.size
    
    fun setSelected(category: String?) {
        selectedCategory = category
        notifyDataSetChanged()
    }
    
    fun updateList(newCategories: List<String>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
