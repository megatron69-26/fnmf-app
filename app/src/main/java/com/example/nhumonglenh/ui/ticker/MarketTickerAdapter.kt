package com.example.nhumonglenh.ui.ticker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.R
import com.example.nhumonglenh.databinding.ItemMarketTickerBinding

class MarketTickerAdapter(
    private var items: List<MarketTickerUiModel> = MarketTickerPolicy.createDefaultTickerList(),
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<MarketTickerAdapter.TickerViewHolder>() {

    inner class TickerViewHolder(val binding: ItemMarketTickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TickerViewHolder {
        val binding = ItemMarketTickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TickerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TickerViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val b = holder.binding

        b.tvTickerSymbol.text = item.displayName
        b.tvTickerPrice.text = MarketTickerPolicy.formatPrice(item.price)
        b.tvTickerChange.text = MarketTickerPolicy.formatChange(item.change24h)
        b.tvTickerChange.setTextColor(MarketTickerPolicy.resolveChangeColor(item.change24h))

        b.viewTickerStale.visibility = if (item.isStale) View.VISIBLE else View.GONE
        b.root.alpha = if (item.isStale) 0.75f else 1.0f

        val priceStr = MarketTickerPolicy.formatPrice(item.price)
        val changeStr = MarketTickerPolicy.formatChange(item.change24h)
        val baseDesc = context.getString(R.string.market_ticker_item_desc, item.displayName, priceStr, changeStr)
        b.root.contentDescription = if (item.isStale) {
            "$baseDesc, ${context.getString(R.string.market_ticker_stale_hint)}"
        } else {
            baseDesc
        }

        b.root.setOnClickListener {
            onItemClick(item.symbol)
        }
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<MarketTickerUiModel> = items

    fun submitList(newItems: List<MarketTickerUiModel>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
