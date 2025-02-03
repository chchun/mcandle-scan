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
import kotlin.random.Random
import vpos.apipackage.At

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BLEDeviceAdapter
    private lateinit var btnScan: Button
    private lateinit var btnNScan: Button
    private lateinit var btnClear: Button
    private var isScanning = false
    private var scanJob: Job? = null
    private val deviceList = mutableListOf<DeviceModel>()
    private val USE_SIMULATOR_MODE = true  // 시뮬레이션 모드 사용 여부

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        btnScan = findViewById(R.id.btnScan)
        btnNScan = findViewById(R.id.btnNScan)
        btnClear = findViewById(R.id.btnClear)

        adapter = BLEDeviceAdapter(deviceList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 1 Scan 버튼 클릭 시 단일 스캔 실행
        btnScan.setOnClickListener {
            if (USE_SIMULATOR_MODE) {
                startScanSimul()
            } else {
                startScan()
            }
        }

        // n Scan 버튼 클릭 시 반복 스캔 실행
        btnNScan.setOnClickListener {
            if (isScanning) {
                stopScanLoop()
            } else {
                if (deviceList.isNotEmpty()) {
                    clearDeviceList()  // 카드 목록이 있을 경우 Clear 후 실행
                }
                startScanLoop()
            }
        }

        // Clear 버튼 클릭 시 목록 초기화
        btnClear.setOnClickListener {
            clearDeviceList()
        }
    }

    private fun startScan() {
        Log.d("BLE_SCAN", "Starting single BLE scan...")
        sendPromptMsg("SCANNING\n")

        lifecycleScope.launch(Dispatchers.IO) {
            delay(2000)  // BLE 스캔 대기 시간 (예제)
            val scannedDevices = listOf(
                DeviceModel("Device_X", "AA:BB:CC:DD:EE:FF", -60)
            )
            updateDeviceList(scannedDevices)
        }
    }

    // 시뮬레이션 모드에서 BLE 스캔 실행
    private fun startScanSimul() {
        Log.d("BLE_SCAN", "Starting simulated BLE scan...")
        sendPromptMsg("SIMULATED SCANNING\n")

        val possibleDevices = listOf(
            DeviceModel("Mi Band 6", "00:11:22:33:44:55", 0),
            DeviceModel("Apple AirTag", "66:77:88:99:AA:BB", 0),
            DeviceModel("Galaxy Watch 5", "CC:DD:EE:FF:00:11", 0),
            DeviceModel("Fitbit Charge 5", "22:33:44:55:66:77", 0),
            DeviceModel("Tile Tracker", "88:99:AA:BB:CC:DD", 0),
            DeviceModel("Bose QC Earbuds", "11:22:33:44:55:66", 0),
            DeviceModel("Garmin Forerunner 945", "77:88:99:AA:BB:CC", 0),
            DeviceModel("JBL Bluetooth Speaker", "99:AA:BB:CC:DD:EE", 0),
            DeviceModel("Sony WH-1000XM4", "11:22:33:44:55:77", 0),
            DeviceModel("Nintendo Switch Pro Controller", "88:99:AA:BB:CC:00", 0)
        )

        val deviceCount = Random.nextInt(2, 8)
        val simulatedDevices = List(deviceCount) {
            val device = possibleDevices[Random.nextInt(possibleDevices.size)]
            DeviceModel(device.name, device.address, Random.nextInt(-100, -50))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            delay(2000)
            updateDeviceList(simulatedDevices)
        }
    }

    // n Scan 실행 (반복 스캔)
    private fun startScanLoop() {
        isScanning = true
        btnNScan.text = "Stop"
        setButtonsEnabled(false)  // 다른 버튼 비활성화
        Log.d("BLE_SCAN", "Starting continuous scan...")

        scanJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isScanning) {
                if (USE_SIMULATOR_MODE) {
                    startScanSimul()
                } else {
                    startScan()
                }
                delay(5000)  // 5초마다 스캔 반복
            }
        }
    }

    // n Scan 중지 (Stop 버튼 클릭 시)
    private fun stopScanLoop() {
        isScanning = false
        btnNScan.text = "n Scan"
        setButtonsEnabled(true)  // 다른 버튼 다시 활성화
        scanJob?.cancel()
        sendPromptMsg("Scan Stop")  // Stop 시 Toast 메시지 출력
        Log.d("BLE_SCAN", "Scan stopped.")
    }

    // 1Scan 및 Clear 버튼 활성화/비활성화 설정
    private fun setButtonsEnabled(isEnabled: Boolean) {
        runOnUiThread {
            btnScan.isEnabled = isEnabled
            btnClear.isEnabled = isEnabled
        }
    }

    // 목록 초기화 함수
    private fun clearDeviceList() {
        deviceList.clear()
        adapter.notifyDataSetChanged()
        sendPromptMsg("목록이 초기화되었습니다.")
        Log.d("BLE_SCAN", "Device list cleared.")
    }

    private suspend fun updateDeviceList(newDevices: List<DeviceModel>) {
        withContext(Dispatchers.Main) {
            var newDeviceCount = 0
            var updatedDeviceCount = 0
            var disabledDeviceCount = 0

            // 기존 장치 중에서 이번 스캔에 포함되지 않은 장치를 비활성화 (RSSI -100)
            for (device in deviceList) {
                if (newDevices.none { it.address == device.address }) {
                    device.rssi = -100
                    disabledDeviceCount++
                }
            }

            // 신규 및 업데이트된 장치 처리
            for (newDevice in newDevices) {
                val existingDevice = deviceList.find { it.address == newDevice.address }
                if (existingDevice != null) {
                    existingDevice.rssi = newDevice.rssi
                    updatedDeviceCount++
                } else {
                    deviceList.add(newDevice)
                    newDeviceCount++
                }
            }

            adapter.notifyDataSetChanged()

            sendPromptMsg(
                "DEVICE UPDATED:\nNew: $newDeviceCount, Update: $updatedDeviceCount, Disable: $disabledDeviceCount"
            )
        }
    }

    private fun sendPromptMsg(message: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
        Log.d("BLE_SCAN", message)
    }
}
