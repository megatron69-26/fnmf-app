package com.example.nhumonglenh

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.data.remote.OrderResponse

class OrderHistoryAdapter(private var orders: List<OrderResponse>) :
    RecyclerView.Adapter<OrderHistoryAdapter.OrderViewHolder>() {

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSymbol: TextView = view.findViewById(R.id.tvOrderSymbol)
        val tvType: TextView = view.findViewById(R.id.tvOrderType)
        val tvQty: TextView = view.findViewById(R.id.tvOrderQty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_history, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        holder.tvSymbol.text = order.symbol ?: "N/A"
        
        val type = order.type ?: ""
        holder.tvType.text = type
        if (type.equals("BUY", true)) {
            holder.tvType.setTextColor(Color.parseColor("#089981"))
        } else {
            holder.tvType.setTextColor(Color.parseColor("#F23645"))
        }

        holder.tvQty.text = "Qty: ${order.quantity ?: 0.0}"
    }

    override fun getItemCount() = orders.size

    fun updateOrders(newOrders: List<OrderResponse>) {
        this.orders = newOrders
        notifyDataSetChanged()
    }
}
