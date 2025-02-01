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
    private lateinit var btnScan: Button // ✅ 스캔 버튼 추가
    private val deviceList = mutableListOf<String>() // BLE 장치 정보 저장

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        btnScan = findViewById(R.id.btnScan) // ✅ 버튼 초기화
        adapter = BLEDeviceAdapter(deviceList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // ✅ 버튼 클릭 시 BLE 스캔 시작
        btnScan.setOnClickListener {
            startScan()
        }
    }

    private fun startScan() {
        Log.d("BLE_SCAN", "Initializing BLE master mode...")

        // ✅ BLE 마스터 모드 활성화
        val ret = At.Lib_EnableMaster(true)
        if (ret == 0) {
            Log.d("BLE_SCAN", "Start master succeeded!")
            SendPromptMsg("Start master succeeded!\n")
        } else {
            Log.e("BLE_SCAN", "Start master failed, return: $ret")
            SendPromptMsg("Start beacon failed, return: $ret\n")
            return // ✅ 마스터 모드 활성화 실패 시 스캔을 시작하지 않음
        }

        // ✅ BLE 스캔 시작
        Log.d("BLE_SCAN", "Starting BLE scan...")
        SendPromptMsg("SCANNING\n")

        val scanResult = At.Lib_AtStartScan(10) // 10초 동안 스캔 실행
        if (scanResult != 0) {
            Log.e("BLE_SCAN", "ERROR WHILE STARTING SCAN, RET = $scanResult")
            SendPromptMsg("ERROR WHILE STARTING SCAN, RET = $scanResult\n")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val devices = Array(20) { "" }

            for (i in 0 until 10) { // 2초 간격으로 10회 반복
                delay(2000)
                val ret = At.Lib_GetScanResult(3, devices) // 3초 동안 BLE 장치 검색
                if (ret == 0) {
                    for (device in devices) {
                        if (!device.isNullOrEmpty() && !deviceList.contains(device)) {
                            Log.d("BLE_SCAN", "NEW DEVICE DISCOVERED: $device")
                            SendPromptMsg("NEW DEVICE DISCOVERED: $device\n")

                            withContext(Dispatchers.Main) {
                                deviceList.add(device)
                                adapter.notifyItemInserted(deviceList.size - 1) // RecyclerView 업데이트
                            }
                        }
                    }
                }
            }
        }
    }

    // ✅ Toast 메시지를 `lifecycleScope.launch`를 사용하여 안전하게 실행
    private fun SendPromptMsg(message: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
        Log.d("BLE_SCAN", message) // 기존 Log 출력 유지
    }
}