package com.example.nhumonglenh.ui.profile

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.remote.HoldingDto
import com.example.nhumonglenh.databinding.ItemHoldingBinding
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
        holder.binding.tvHoldingQuantity.text = String.format(Locale.US, "%.4f", item.quantity ?: 0.0)
        holder.binding.tvHoldingAvgPrice.text = String.format(Locale.US, "$%,.2f", item.avgBuyPrice ?: 0.0)
        holder.binding.tvHoldingCurrentPrice.text = String.format(Locale.US, "$%,.2f", item.currentPrice ?: 0.0)

        val pnl = item.unrealizedPnL ?: 0.0
        val colorRes = if (pnl >= 0) R.color.tv_green else R.color.tv_red
        val sign = if (pnl >= 0) "+" else "-"
        val formattedPnl = String.format(Locale.US, "%s$%,.2f", sign, abs(pnl))

        holder.binding.tvHoldingPnl.text = formattedPnl
        holder.binding.tvHoldingPnl.setTextColor(ContextCompat.getColor(context, colorRes))
    }

    override fun getItemCount(): Int = holdings.size

    fun submitList(newList: List<HoldingDto>) {
        this.holdings = newList
        notifyDataSetChanged()
    }
}
