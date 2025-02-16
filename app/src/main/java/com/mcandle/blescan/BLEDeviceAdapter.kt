package com.mcandle.blescan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BLEDeviceAdapter(
    private val deviceList: MutableList<DeviceModel>,
    private val onDeviceClick: (String) -> Unit // 🔹 클릭 이벤트 콜백 추가
) : RecyclerView.Adapter<BLEDeviceAdapter.BLEDeviceViewHolder>() {

    class BLEDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceAddressTextView: TextView = itemView.findViewById(R.id.deviceAddress)
        val deviceRssiTextView: TextView = itemView.findViewById(R.id.deviceRssi)
        val deviceNameTextView: TextView = itemView.findViewById(R.id.deviceName)
        val deviceRssiIcon: ImageView = itemView.findViewById(R.id.deviceRssiIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BLEDeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ble_device, parent, false)
        return BLEDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BLEDeviceViewHolder, position: Int) {
        val device = deviceList[position]

        holder.deviceAddressTextView.text = device.address
        holder.deviceRssiTextView.text = "${device.rssi} dBm"
        holder.deviceNameTextView.text = device.name

        // 🔹 클릭 이벤트를 MainActivity에서 처리하도록 콜백 호출
        holder.itemView.setOnClickListener {
            onDeviceClick(device.address)
        }
    }

    override fun getItemCount() = deviceList.size
}
