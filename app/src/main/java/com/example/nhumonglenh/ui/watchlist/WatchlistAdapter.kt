package com.example.nhumonglenh.ui.watchlist

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.R

/**
 * WatchlistAdapter — kế thừa từ UI Hùng v3.1 kết hợp dữ liệu Live Backend & Room DB
 */
class WatchlistAdapter(
    private var items: List<WatchlistUiModel>,
    private val onClick: (WatchlistUiModel) -> Unit
) : RecyclerView.Adapter<WatchlistAdapter.ViewHolder>() {

    private val iconColors = listOf(
        0xFFF7931A.toInt(), // BTC — cam vàng Bitcoin
        0xFF627EEA.toInt(), // ETH — tím Ethereum
        0xFFF0B90B.toInt(), // BNB — vàng Binance
        0xFF9945FF.toInt(), // SOL — tím Solana
        0xFF346AA9.toInt(), // XRP — xanh dương Ripple
        0xFF0033AD.toInt(), // ADA — xanh Cardano
        0xFFBA2121.toInt(), // DOGE — đỏ Dogecoin
        0xFFE6007A.toInt(), // DOT — hồng Polkadot
        0xFF2775CA.toInt(), // USDC — xanh dương
        0xFF26A17B.toInt()  // USDT — xanh lá Tether
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCoinIcon: TextView = view.findViewById(R.id.tvCoinIcon)
        val tvSymbol: TextView = view.findViewById(R.id.tvSymbol)
        val tvFullName: TextView = view.findViewById(R.id.tvFullName)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvChangePercent: TextView = view.findViewById(R.id.tvChangePercent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_watchlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 1. Icon — lấy 2-3 ký tự đầu của symbol
        val cleanSym = item.symbol.replace("/", "").replace("USDT", "").replace("USD", "")
        val initials = if (cleanSym.isNotEmpty()) cleanSym.take(3).uppercase() else item.symbol.take(2).uppercase()
        holder.tvCoinIcon.text = initials
        val iconColor = if (item.iconColor != 0) item.iconColor else iconColors[position % iconColors.size]
        val iconBg = holder.tvCoinIcon.background as? GradientDrawable
            ?: GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                holder.tvCoinIcon.background = this
            }
        iconBg.setColor(iconColor)

        // 2. Symbol và tên đầy đủ
        holder.tvSymbol.text = item.symbol
        holder.tvFullName.text = item.fullName

        // 3. Giá
        holder.tvPrice.text = formatPrice(item.price)

        // 4. % thay đổi
        val changeStr = if (item.changePercent >= 0) {
            "+%.2f%%".format(item.changePercent)
        } else {
            "%.2f%%".format(item.changePercent)
        }
        holder.tvChangePercent.text = changeStr

        val badgeBg = holder.tvChangePercent.background as? GradientDrawable
            ?: GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f
                holder.tvChangePercent.background = this
            }

        if (item.changePercent >= 0) {
            badgeBg.setColor(0xFF089981.toInt()) // xanh lá TradingView
        } else {
            badgeBg.setColor(0xFFF23645.toInt()) // đỏ TradingView
        }

        // 5. Click chuyển sang biểu đồ nến
        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<WatchlistUiModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatPrice(price: Double): String {
        return when {
            price >= 1000 -> "$%,.2f".format(price)
            price >= 1 -> "$%,.4f".format(price)
            else -> "$%.6f".format(price)
        }
    }
}
