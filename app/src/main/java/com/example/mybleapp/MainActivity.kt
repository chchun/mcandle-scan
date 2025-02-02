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

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BLEDeviceAdapter
    private lateinit var btnScan: Button
    private lateinit var btnClear: Button  // Clear 버튼 추가
    private val deviceList = mutableListOf<String>()
    private val USE_SIMULATOR_MODE = true // 시뮬레이터 모드 플래그

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        btnScan = findViewById(R.id.btnScan)
        btnClear = findViewById(R.id.btnClear)  // Clear 버튼 초기화

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

        // Clear 버튼 클릭 시 BLE 목록 초기화
        btnClear.setOnClickListener {
            deviceList.clear() // 목록 초기화
            adapter.notifyDataSetChanged() // RecyclerView 갱신
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
                    updateDeviceList(devices)
                }
            }
        }
    }

    private fun startScanSimul() {
        Log.d("BLE_SCAN", "Starting simulated BLE scan...")
        sendPromptMsg("SIMULATED SCANNING\n")

        val simulatedDevices = listOf(
            "00:11:22:33:44:55 -50 Device_A",
            "66:77:88:99:AA:BB -60 Device_B",
            "CC:DD:EE:FF:00:11 -70 Device_C",
            "22:33:44:55:66:77 -80 Device_D",
            "88:99:AA:BB:CC:DD -90 Device_E"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            delay(2000)
            updateDeviceList(simulatedDevices.toTypedArray())
        }
    }

    private suspend fun updateDeviceList(devices: Array<String>) {
        for (device in devices) {
            if (!device.isNullOrEmpty() && !deviceList.contains(device)) {
                val deviceInfo = device.split(" ")
                val deviceAddress = deviceInfo.getOrNull(0) ?: "Unknown"
                val deviceRssi = deviceInfo.getOrNull(1) ?: "Unknown"
                val deviceName = deviceInfo.getOrNull(2) ?: "Unknown"

                val formattedDeviceInfo = "address: $deviceAddress, rssi: $deviceRssi, device name: $deviceName"
                Log.d("BLE_SCAN", "NEW DEVICE DISCOVERED: $formattedDeviceInfo")
                sendPromptMsg("NEW DEVICE DISCOVERED: $formattedDeviceInfo\n")

                withContext(Dispatchers.Main) {
                    deviceList.add(formattedDeviceInfo)
                    adapter.notifyItemInserted(deviceList.size - 1)
                }
            }
        }
    }

    private fun sendPromptMsg(message: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
        Log.d("BLE_SCAN", message)
    }
}
