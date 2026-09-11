package com.example.nhumonglenh.ui.payment

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.remote.PaymentOrderDto
import com.google.android.material.button.MaterialButton
import java.util.Locale

object PaymentItemFormatter {
    fun formatType(type: String?): String {
        return when (type?.trim()?.uppercase(Locale.ROOT)) {
            "DEPOSIT" -> "NẠP"
            "WITHDRAWAL" -> "RÚT"
            else -> "—"
        }
    }

    fun formatAmount(amount: Double?, type: String?): String {
        if (amount == null) return "—"
        val normalizedType = type?.trim()?.uppercase(Locale.ROOT)
        return when (normalizedType) {
            "DEPOSIT" -> String.format(Locale.US, "+$%,.2f", amount)
            "WITHDRAWAL" -> String.format(Locale.US, "-$%,.2f", amount)
            else -> String.format(Locale.US, "$%,.2f", amount)
        }
    }

    fun formatOrderId(orderId: Long?): String {
        return if (orderId != null && orderId > 0) "#$orderId" else "#—"
    }

    fun formatCreatedAt(createdAt: String?): String {
        return createdAt?.replace("T", " ")?.substringBefore(".") ?: "—"
    }

    fun formatStatus(status: String?): String {
        return status?.trim()?.uppercase(Locale.ROOT) ?: "UNKNOWN"
    }

    fun formatStatusLabel(status: String?): String {
        return when (formatStatus(status)) {
            "SUCCEEDED" -> "THÀNH CÔNG"
            "PENDING" -> "ĐANG CHỜ"
            "PROCESSING" -> "ĐANG XỬ LÝ"
            "FAILED" -> "THẤT BẠI"
            "CANCELLED" -> "ĐÃ HỦY"
            else -> "CHƯA XÁC ĐỊNH"
        }
    }

    fun shouldShowPendingActions(status: String?, orderId: Long?): Boolean {
        val s = formatStatus(status)
        return (s == "PENDING" || s == "PROCESSING") && (orderId != null && orderId > 0)
    }
}

class PaymentHistoryAdapter(
    private val onOpenCheckout: (PaymentOrderDto) -> Unit,
    private val onCancelOrder: (PaymentOrderDto) -> Unit
) : RecyclerView.Adapter<PaymentHistoryAdapter.PaymentViewHolder>() {

    private val items = mutableListOf<PaymentOrderDto>()

    fun submitList(newItems: List<PaymentOrderDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_payment_history, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTypeBadge: TextView = itemView.findViewById(R.id.tv_payment_type_badge)
        private val tvOrderId: TextView = itemView.findViewById(R.id.tv_payment_order_id)
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_payment_amount)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_payment_time)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tv_payment_status_badge)
        private val layoutPending: View = itemView.findViewById(R.id.layout_pending_actions)
        private val btnCancel: MaterialButton = itemView.findViewById(R.id.btn_cancel_payment)
        private val btnOpenCheckout: MaterialButton = itemView.findViewById(R.id.btn_open_checkout)

        fun bind(item: PaymentOrderDto) {
            // 1. Loại giao dịch (NẠP / RÚT / KHÔNG RÕ)
            val type = item.type?.trim()?.uppercase(Locale.ROOT)
            val isDeposit = type == "DEPOSIT"
            val isWithdrawal = type == "WITHDRAWAL"

            tvTypeBadge.text = PaymentItemFormatter.formatType(item.type)
            if (isDeposit) {
                tvTypeBadge.setBackgroundColor(Color.parseColor("#064E3B"))
                tvTypeBadge.setTextColor(Color.parseColor("#34D399"))
            } else if (isWithdrawal) {
                tvTypeBadge.setBackgroundColor(Color.parseColor("#7F1D1D"))
                tvTypeBadge.setTextColor(Color.parseColor("#FCA5A5"))
            } else {
                tvTypeBadge.setBackgroundColor(Color.parseColor("#334155"))
                tvTypeBadge.setTextColor(Color.parseColor("#94A3B8"))
            }

            // 2. Số tiền (USD) - Hiển thị "—" nếu thiếu trường, tuyệt đối không bịa số 0.00
            val amount = item.amountUsd
            tvAmount.text = PaymentItemFormatter.formatAmount(item.amountUsd, item.type)
            if (amount != null) {
                if (isDeposit) {
                    tvAmount.setTextColor(Color.parseColor("#34D399"))
                } else if (isWithdrawal) {
                    tvAmount.setTextColor(Color.parseColor("#F87171"))
                } else {
                    tvAmount.setTextColor(Color.parseColor("#E2E8F0"))
                }
            } else {
                tvAmount.setTextColor(Color.parseColor("#94A3B8"))
            }

            // 3. Mã đơn hàng - Hiển thị "#—" nếu thiếu trường, không tạo ID #0 giả
            val orderId = item.paymentOrderId
            tvOrderId.text = PaymentItemFormatter.formatOrderId(item.paymentOrderId)

            // 4. Thời gian tạo
            tvTime.text = PaymentItemFormatter.formatCreatedAt(item.createdAt)

            // 5. Trạng thái - Mặc định "UNKNOWN" nếu thiếu trường, không tự gán "PENDING"
            val status = PaymentItemFormatter.formatStatus(item.status)
            tvStatusBadge.text = PaymentItemFormatter.formatStatusLabel(item.status)

            when (status) {
                "SUCCEEDED" -> {
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#064E3B"))
                    tvStatusBadge.setTextColor(Color.parseColor("#34D399"))
                    layoutPending.visibility = View.GONE
                }
                "PENDING", "PROCESSING" -> {
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#854D0E"))
                    tvStatusBadge.setTextColor(Color.parseColor("#FDE047"))

                    // Chỉ mở nút hành động khi có orderId hợp lệ
                    if (PaymentItemFormatter.shouldShowPendingActions(item.status, item.paymentOrderId)) {
                        layoutPending.visibility = View.VISIBLE
                        btnOpenCheckout.isEnabled = !item.checkoutUrl.isNullOrBlank()
                        btnOpenCheckout.setOnClickListener { onOpenCheckout(item) }
                        btnCancel.isEnabled = true
                        btnCancel.setOnClickListener { onCancelOrder(item) }
                    } else {
                        layoutPending.visibility = View.GONE
                    }
                }
                "FAILED" -> {
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#7F1D1D"))
                    tvStatusBadge.setTextColor(Color.parseColor("#F87171"))
                    layoutPending.visibility = View.GONE
                }
                "CANCELLED" -> {
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#334155"))
                    tvStatusBadge.setTextColor(Color.parseColor("#CBD5E1"))
                    layoutPending.visibility = View.GONE
                }
                else -> {
                    // UNKNOWN hoặc trạng thái không xác định: khóa toàn bộ thao tác
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#1E293B"))
                    tvStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
                    layoutPending.visibility = View.GONE
                }
            }
        }
    }
}
