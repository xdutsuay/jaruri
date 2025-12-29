package com.example.moneymanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R

class CategoryAdapter(private val categories: List<CategoryItem>) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    data class CategoryItem(val name: String, val iconRes: Int)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvCategoryName)
        val delete: ImageView = view.findViewById(R.id.ivDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // reuse item_transaction or create a new item_category?
        // item_transaction is complex. Better create simple item_category.
        // I'll assume valid layout 'item_category' exists or create it.
        // I will create 'item_category.xml'.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = categories[position]
        holder.name.text = item.name
        // holder.icon.setImageResource(item.iconRes) // Placeholder
        holder.icon.setImageResource(android.R.drawable.ic_menu_gallery)
        
        holder.delete.setOnClickListener {
            // Callback to delete
        }
    }

    override fun getItemCount() = categories.size
}
