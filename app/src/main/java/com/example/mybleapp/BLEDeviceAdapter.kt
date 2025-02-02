package com.example.mybleapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BLEDeviceAdapter(private val deviceList: MutableList<DeviceModel>) :
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
        val device = deviceList[position]  // 🔹 DeviceModel 사용

        holder.deviceAddressTextView.text = device.address
        holder.deviceRssiTextView.text = "${device.rssi} dBm"
        holder.deviceNameTextView.text = device.name
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }
}
