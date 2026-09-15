package com.example.nhumonglenh.ui.profile

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.remote.HoldingDto
import com.example.nhumonglenh.databinding.ItemHoldingBinding
import com.example.nhumonglenh.ui.trading.PortfolioValuationPolicy
import java.util.Locale
import kotlin.math.abs

class HoldingAdapter(
    private var holdings: List<HoldingDto> = emptyList()
) : RecyclerView.Adapter<HoldingAdapter.HoldingViewHolder>() {

    class HoldingViewHolder(val binding: ItemHoldingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HoldingViewHolder {
        val binding = ItemHoldingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HoldingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HoldingViewHolder, position: Int) {
        val item = holdings[position]
        val context = holder.itemView.context

        holder.binding.tvHoldingSymbol.text = item.symbol ?: "—"
        holder.binding.tvHoldingQuantity.text = PortfolioValuationPolicy.formatHoldingQuantity(item.quantity)
        holder.binding.tvHoldingAvgPrice.text = PortfolioValuationPolicy.formatHoldingAvgPrice(item.avgBuyPrice)

        val currentPrice = item.currentPrice
        if (currentPrice != null && currentPrice > 0.0) {
            holder.binding.tvHoldingCurrentPrice.text = String.format(Locale.US, "$%,.2f", currentPrice)
            val pnl = item.unrealizedPnL
            if (pnl != null) {
                val colorRes = if (pnl >= 0) R.color.tv_green else R.color.tv_red
                val sign = if (pnl >= 0) "+" else "-"
                val formattedPnl = String.format(Locale.US, "%s$%,.2f", sign, abs(pnl))
                holder.binding.tvHoldingPnl.text = formattedPnl
                holder.binding.tvHoldingPnl.setTextColor(ContextCompat.getColor(context, colorRes))
            } else {
                holder.binding.tvHoldingPnl.text = "—"
                holder.binding.tvHoldingPnl.setTextColor(ContextCompat.getColor(context, R.color.tv_text_secondary))
            }
        } else {
            holder.binding.tvHoldingCurrentPrice.text = "—"
            holder.binding.tvHoldingPnl.text = "—"
            holder.binding.tvHoldingPnl.setTextColor(ContextCompat.getColor(context, R.color.tv_text_secondary))
        }
    }

    override fun getItemCount(): Int = holdings.size

    fun submitList(newList: List<HoldingDto>) {
        this.holdings = newList
        notifyDataSetChanged()
    }
}
