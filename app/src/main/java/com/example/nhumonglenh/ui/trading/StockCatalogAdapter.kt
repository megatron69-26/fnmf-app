package com.example.nhumonglenh.ui.trading

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.R
import com.example.nhumonglenh.databinding.ItemStockCatalogBinding

class StockCatalogAdapter(
    private var items: List<StockCatalogUiModel> = emptyList(),
    private val onItemClick: (StockCatalogUiModel) -> Unit,
    private val onWatchlistToggle: (StockCatalogUiModel) -> Unit
) : RecyclerView.Adapter<StockCatalogAdapter.ViewHolder>() {

    private val iconColors = listOf(
        0xFF1E88E5.toInt(), // Xanh dương
        0xFF00897B.toInt(), // Xanh ngọc
        0xFF43A047.toInt(), // Xanh lá
        0xFFFB8C00.toInt(), // Cam
        0xFF8E24AA.toInt(), // Tím
        0xFFE53935.toInt(), // Đỏ
        0xFF3949AB.toInt(), // Indigo
        0xFF00ACC1.toInt()  // Cyan
    )

    inner class ViewHolder(val binding: ItemStockCatalogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStockCatalogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val b = holder.binding

        b.tvStockSymbol.text = item.symbol
        b.tvStockName.text = item.name

        // Icon tròn với ký tự viết tắt
        b.tvStockIcon.text = item.symbol.take(2).uppercase()
        val iconColor = iconColors[position % iconColors.size]
        val iconBg = b.tvStockIcon.background as? GradientDrawable
            ?: GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                b.tvStockIcon.background = this
            }
        iconBg.setColor(iconColor)

        // Hiển thị Giá & % Biến động 24h
        if (item.price != null && item.price > 0.0) {
            b.tvStockPrice.visibility = android.view.View.VISIBLE
            b.tvStockPrice.text = String.format(java.util.Locale.US, "$%,.2f", item.price)
        } else {
            b.tvStockPrice.visibility = android.view.View.GONE
        }

        if (item.change24h != null) {
            b.tvStockChange24h.visibility = android.view.View.VISIBLE
            val sign = if (item.change24h >= 0) "+" else ""
            b.tvStockChange24h.text = String.format(java.util.Locale.US, "%s%.2f%%", sign, item.change24h)
            val colorRes = if (item.change24h >= 0) R.color.tv_green else R.color.tv_red
            b.tvStockChange24h.setTextColor(ContextCompat.getColor(context, colorRes))
        } else {
            b.tvStockChange24h.visibility = android.view.View.GONE
        }

        // Nút Quan tâm / Đã quan tâm
        if (item.isWatchlisted) {
            b.btnWatchlistAction.text = context.getString(R.string.btn_watchlist_remove)
            b.btnWatchlistAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.tv_surface))
            b.btnWatchlistAction.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.tv_green))
            b.btnWatchlistAction.strokeWidth = 2
            b.btnWatchlistAction.setTextColor(ContextCompat.getColor(context, R.color.tv_green))
        } else {
            b.btnWatchlistAction.text = context.getString(R.string.btn_watchlist_add)
            b.btnWatchlistAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.tv_green))
            b.btnWatchlistAction.strokeWidth = 0
            b.btnWatchlistAction.setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        b.btnWatchlistAction.setOnClickListener {
            onWatchlistToggle(item)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<StockCatalogUiModel>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
