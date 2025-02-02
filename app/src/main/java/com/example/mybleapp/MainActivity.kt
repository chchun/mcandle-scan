package com.example.mybleapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import vpos.apipackage.At
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BLEDeviceAdapter
    private lateinit var btnScan: Button
    private lateinit var btnClear: Button
    private val deviceList = mutableListOf<DeviceModel>() // 🔹 DeviceModel 리스트로 변경
    private val USE_SIMULATOR_MODE = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        btnScan = findViewById(R.id.btnScan)
        btnClear = findViewById(R.id.btnClear)

        adapter = BLEDeviceAdapter(deviceList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnScan.setOnClickListener {
            if (USE_SIMULATOR_MODE) {
                startScanSimul()
            } else {
                startScan()
            }
        }

        btnClear.setOnClickListener {
            deviceList.clear()
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "목록이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
            Log.d("BLE_SCAN", "Device list cleared.")
        }
    }

    private fun startScan() {
        Log.d("BLE_SCAN", "Initializing BLE master mode...")
        val ret = At.Lib_EnableMaster(true)
        if (ret != 0) {
            Log.e("BLE_SCAN", "Start master failed, return: $ret")
            sendPromptMsg("Start beacon failed, return: $ret\n")
            return
        }

        Log.d("BLE_SCAN", "Starting BLE scan...")
        sendPromptMsg("SCANNING\n")

        val scanResult = At.Lib_AtStartScan(10)
        if (scanResult != 0) {
            Log.e("BLE_SCAN", "ERROR WHILE STARTING SCAN, RET = $scanResult")
            sendPromptMsg("ERROR WHILE STARTING SCAN, RET = $scanResult\n")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val devices = Array(20) { "" }
            for (i in 0 until 10) {
                delay(2000)
                val getScanResult = At.Lib_GetScanResult(3, devices)
                if (getScanResult == 0) {
                    val parsedDevices = devices.filter { it.isNotEmpty() }.map { parseDevice(it) }
                    updateDeviceList(parsedDevices)
                }
            }
        }
    }

    private fun startScanSimul() {
        Log.d("BLE_SCAN", "Starting simulated BLE scan...")
        sendPromptMsg("SIMULATED SCANNING\n")

        val simulatedDevices = listOf(
            DeviceModel("Device_A", "00:11:22:33:44:55", Random.nextInt(-100, -50)),
            DeviceModel("Device_B", "66:77:88:99:AA:BB", Random.nextInt(-100, -50)),
            DeviceModel("Device_C", "CC:DD:EE:FF:00:11", Random.nextInt(-100, -50)),
            DeviceModel("Device_D", "22:33:44:55:66:77", Random.nextInt(-100, -50)),
            DeviceModel("Device_E", "88:99:AA:BB:CC:DD", Random.nextInt(-100, -50))
        )

        lifecycleScope.launch(Dispatchers.IO) {
            delay(2000)
            updateDeviceList(simulatedDevices)
        }
    }

    private suspend fun updateDeviceList(newDevices: List<DeviceModel>) {
        withContext(Dispatchers.Main) {
            val updatedDevices = mutableListOf<String>()

            for (newDevice in newDevices) {
                val existingDevice = deviceList.find { it.address == newDevice.address }
                if (existingDevice != null) {
                    existingDevice.rssi = newDevice.rssi
                    adapter.notifyDataSetChanged()
                } else {
                    deviceList.add(newDevice)
                    adapter.notifyItemInserted(deviceList.size - 1)
                }
                updatedDevices.add("address: ${newDevice.address}, rssi: ${newDevice.rssi}, name: ${newDevice.name}")
            }

            if (updatedDevices.isNotEmpty()) {
                sendPromptMsg("DEVICE UPDATED:\n${updatedDevices.joinToString("\n")}")
            }
        }
    }

    private fun sendPromptMsg(message: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
        Log.d("BLE_SCAN", message)
    }

    private fun parseDevice(deviceString: String): DeviceModel {
        val parts = deviceString.split(" ")
        return DeviceModel(
            name = parts.getOrNull(2) ?: "Unknown",
            address = parts.getOrNull(0) ?: "Unknown",
            rssi = parts.getOrNull(1)?.toIntOrNull() ?: -100
        )
    }
}
