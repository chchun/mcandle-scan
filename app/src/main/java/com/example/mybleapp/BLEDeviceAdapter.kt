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
        val deviceRssiIcon: ImageView = itemView.findViewById(R.id.deviceRssiIcon) // RSSI 아이콘 추가
        val txPowerTextView: TextView = itemView.findViewById(R.id.tvTxPower)
        val bondStateTextView: TextView = itemView.findViewById(R.id.tvBondState)
        val manufacturerDataTextView: TextView = itemView.findViewById(R.id.tvManufacturerData)
        val serviceUuidsTextView: TextView = itemView.findViewById(R.id.tvServiceUUIDs)
        val serviceDataTextView: TextView = itemView.findViewById(R.id.tvServiceData)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BLEDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return BLEDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BLEDeviceViewHolder, position: Int) {
        val device = deviceList[position]

        holder.deviceAddressTextView.text = device.address
        holder.deviceRssiTextView.text = "${device.rssi} dBm"
        holder.deviceNameTextView.text = device.name

        holder.txPowerTextView.text = "TX : ${device.txPower ?: "N/A"} dBm"
        holder.txPowerTextView.visibility = if (device.txPower != null) View.VISIBLE else View.GONE

        holder.bondStateTextView.text = "Bond State: ${device.bondState}"
        holder.bondStateTextView.visibility = if (device.bondState != "None") View.VISIBLE else View.GONE

        holder.manufacturerDataTextView.text = "Manufacturer Data: ${device.manufacturerData?.keys?.firstOrNull() ?: "N/A"}"
        holder.manufacturerDataTextView.visibility = if (device.manufacturerData != null) View.VISIBLE else View.GONE

        holder.serviceUuidsTextView.text = "UUIDs: ${device.serviceUuids?.joinToString() ?: "N/A"}"
        holder.serviceUuidsTextView.visibility = if (!device.serviceUuids.isNullOrEmpty()) View.VISIBLE else View.GONE

        holder.serviceDataTextView.text = "Service Data: ${device.serviceData?.keys?.firstOrNull() ?: "N/A"}"
        holder.serviceDataTextView.visibility = if (!device.serviceData.isNullOrEmpty()) View.VISIBLE else View.GONE

        // RSSI 값에 따른 색상 변경 처리
        val context = holder.itemView.context
        val grayColor = ContextCompat.getColor(context, R.color.gray)
        val defaultColor = ContextCompat.getColor(context, R.color.default_text_color)

        if (device.rssi == -100) {
            holder.deviceRssiTextView.setTextColor(grayColor)
            holder.deviceRssiIcon.setColorFilter(grayColor, PorterDuff.Mode.SRC_IN) // 아이콘 색상 변경
        } else {
            holder.deviceRssiTextView.setTextColor(defaultColor)
            holder.deviceRssiIcon.setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN) // 기본 색상 복원
        }
    }

    override fun getItemCount() = deviceList.size
}
