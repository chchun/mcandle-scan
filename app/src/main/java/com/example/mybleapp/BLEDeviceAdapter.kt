package com.example.mybleapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BLEDeviceAdapter(private val deviceList: MutableList<String>) :
    RecyclerView.Adapter<BLEDeviceAdapter.BLEDeviceViewHolder>() {

    class BLEDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceAddressTextView: TextView = itemView.findViewById(R.id.deviceAddress)
        val deviceRssiTextView: TextView = itemView.findViewById(R.id.deviceRssi)
        val deviceNameTextView: TextView = itemView.findViewById(R.id.deviceName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BLEDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return BLEDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BLEDeviceViewHolder, position: Int) {
        val deviceInfo = deviceList[position].split(", ")
        val deviceAddress = deviceInfo.getOrNull(0)?.replace("address: ", "") ?: "Unknown"
        val deviceRssi = deviceInfo.getOrNull(1)?.replace("rssi: ", "") ?: "Unknown"
        val deviceName = deviceInfo.getOrNull(2)?.replace("device name: ", "") ?: "Unknown"

        holder.deviceAddressTextView.text = "$deviceAddress"
        holder.deviceRssiTextView.text = "$deviceRssi dBm"
        holder.deviceNameTextView.text = "$deviceName"
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }
}
