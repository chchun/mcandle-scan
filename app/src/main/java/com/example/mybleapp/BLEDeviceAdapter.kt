package com.example.mybleapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BLEDeviceAdapter(private val deviceList: MutableList<String>) :
    RecyclerView.Adapter<BLEDeviceAdapter.BLEDeviceViewHolder>() {

    class BLEDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceInfoTextView: TextView = itemView.findViewById(R.id.deviceInfoTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BLEDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return BLEDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BLEDeviceViewHolder, position: Int) {
        holder.deviceInfoTextView.text = deviceList[position]
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }
}
