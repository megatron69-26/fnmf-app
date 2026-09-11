package com.example.nhumonglenh.ui.profile

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.remote.OrderResponse
import com.example.nhumonglenh.databinding.ItemTradeTransactionBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TradeHistoryAdapter(
    private var orders: List<OrderResponse> = emptyList()
) : RecyclerView.Adapter<TradeHistoryAdapter.TradeViewHolder>() {

    class TradeViewHolder(val binding: ItemTradeTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TradeViewHolder {
        val binding = ItemTradeTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TradeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TradeViewHolder, position: Int) {
        val order = orders[position]
        val context = holder.itemView.context

        // 1. Phân loại loại giao dịch: BUY, SELL, TOPUP, GRANT hoặc loại khác
        val upperType = order.type?.uppercase(Locale.ROOT)?.trim() ?: ""
        val (badgeText, badgeColor) = when (upperType) {
            "BUY" -> Pair(context.getString(R.string.transaction_buy), R.color.tv_green)
            "SELL" -> Pair(context.getString(R.string.transaction_sell), R.color.tv_red)
            "TOPUP" -> Pair(context.getString(R.string.transaction_topup), R.color.tv_blue)
            "GRANT" -> Pair(context.getString(R.string.transaction_grant), R.color.tv_yellow)
            else -> Pair(order.type ?: context.getString(R.string.no_data), R.color.tv_text_secondary)
        }

        holder.binding.tvOrderTypeBadge.text = badgeText
        holder.binding.tvOrderTypeBadge.setBackgroundColor(ContextCompat.getColor(context, badgeColor))

        // 2. Thông tin mã coin & ID
        holder.binding.tvOrderSymbol.text = order.symbol ?: "—"
        holder.binding.tvOrderId.text = if (order.transactionId != null) "#${order.transactionId}" else ""

        // 3. Tổng tiền
        val total = order.totalAmount ?: ((order.price ?: 0.0) * (order.quantity ?: 0.0))
        holder.binding.tvOrderTotalAmount.text = String.format(Locale.US, "$%,.2f", total)

        // 4. Chi tiết khối lượng & giá khớp
        val qty = order.quantity ?: 0.0
        val price = order.price ?: 0.0
        holder.binding.tvOrderDetails.text = String.format(Locale.US, "Khối lượng: %.4f | Giá khớp: $%,.2f", qty, price)

        // 5. Hiển thị thời gian giao dịch an toàn
        holder.binding.tvOrderTime.text = formatExecutedTime(context, order.executedAt)
    }

    override fun getItemCount(): Int = orders.size

    fun submitList(newList: List<OrderResponse>) {
        this.orders = newList
        notifyDataSetChanged()
    }

    companion object {
        fun formatExecutedTime(context: Context, rawTime: String?): String {
            if (rawTime.isNullOrBlank()) {
                return context.getString(R.string.unknown_time)
            }

            // Xử lý chuỗi ISO (VD: "2026-09-08T11:45:00" hoặc "2026-09-08T11:45:00.123")
            return try {
                val cleanTime = if (rawTime.contains(".")) rawTime.substringBefore(".") else rawTime
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                val date = inputFormat.parse(cleanTime)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault())
                    outputFormat.format(date)
                } else {
                    rawTime
                }
            } catch (_: Exception) {
                // Nếu parse lỗi thì hiển thị chuỗi gốc hoặc "Chưa rõ thời gian"
                if (rawTime.isNotBlank()) rawTime else context.getString(R.string.unknown_time)
            }
        }
    }
}
