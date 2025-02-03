package com.example.mybleapp

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BLEDeviceAdapter(private val deviceList: MutableList<DeviceModel>) :
    RecyclerView.Adapter<BLEDeviceAdapter.BLEDeviceViewHolder>() {

    class BLEDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceAddressTextView: TextView = itemView.findViewById(R.id.deviceAddress)
        val deviceRssiTextView: TextView = itemView.findViewById(R.id.deviceRssi)
        val deviceNameTextView: TextView = itemView.findViewById(R.id.deviceName)
        val deviceRssiIcon: ImageView = itemView.findViewById(R.id.deviceRssiIcon) // 아이콘 추가
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BLEDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return BLEDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BLEDeviceViewHolder, position: Int) {
        val device = deviceList[position]  // 🔹 DeviceModel 사용

        holder.deviceAddressTextView.text = device.address
        holder.deviceRssiTextView.text = "${device.rssi} dBm"
        holder.deviceNameTextView.text = device.name

        val context = holder.itemView.context
        val grayColor = ContextCompat.getColor(context, R.color.gray)
        val defaultColor = ContextCompat.getColor(context, R.color.default_text_color)

        // RSSI 값이 -100일 때 색상 변경
        if (device.rssi == -100) {
            holder.deviceRssiTextView.setTextColor(grayColor)
            holder.deviceRssiIcon.setColorFilter(grayColor, PorterDuff.Mode.SRC_IN) // 아이콘 색상 변경
        } else {
            holder.deviceRssiTextView.setTextColor(defaultColor)
            holder.deviceRssiIcon.setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN) // 기본 색상 복원
        }
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }
}
