package com.mcandle.blescan

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mcandle.blescan.utils.BLEUtils
import kotlinx.coroutines.*

import com.mcandle.blescan.ble.BleManager
import com.mcandle.blescan.ble.SimulManager

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView

    private lateinit var btnScan: Button
    private lateinit var btnNScan: Button
    private lateinit var btnClear: Button
    private lateinit var switchSimul: Switch
    private lateinit var switchServer: Switch

    private var isScanning = false
    private var scanJob: Job? = null

    private var useSimulatorMode = true  // 시뮬레이션 모드 사용 여부
    private var isServerMode = true      // 서버 모드 사용 여부
    private var useRemoteJson = true    // isServerMode 일때  json을 render에서 가져올지

    private lateinit var bleManager: BleManager
    private lateinit var simulManager: SimulManager

    private val deviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: BLEDeviceAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 요소 초기화
        switchServer = findViewById(R.id.switchServer)
        switchSimul = findViewById(R.id.switchSimul)
        recyclerView = findViewById(R.id.recyclerView)
        btnScan = findViewById(R.id.btnScan)
        btnNScan = findViewById(R.id.btnNScan)
        btnClear = findViewById(R.id.btnClear)

    // ✅ 해결: bleManager를 먼저 초기화한 후 사용
        bleManager = BleManager.getInstance(this)
        simulManager = SimulManager.getInstance(this)

        // 🔹 BleManager가 데이터를 업데이트하면 UI 갱신
        bleManager.setUpdateListener { newDevices ->
            runOnUiThread {
                deviceList.clear()
                deviceList.addAll(newDevices)
                adapter.notifyDataSetChanged()  // ✅ UI 업데이트는 MainActivity에서 수행
            }
        }

        // 🔹 RecyclerView Adapter 초기화 (클릭 이벤트 MainActivity에서 처리)
        adapter = BLEDeviceAdapter(deviceList, ::onDeviceSelected)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 🔹 Server 모드 스위치 리스너 추가
        switchServer.setOnCheckedChangeListener { _, isChecked ->
            isServerMode = isChecked
            val mode = if (isChecked) "Server Mode Enabled" else "Server Mode Disabled"
            Toast.makeText(this, mode, Toast.LENGTH_SHORT).show()
        }

        // 🔹 스캔 상태 변경 시 UI 업데이트 리스너 설정
        bleManager.setScanStatusListener { isScanning ->
            runOnUiThread {
                btnNScan.text = if (isScanning) "Stop" else "n Scan"
                btnScan.isEnabled = !isScanning
                btnClear.isEnabled = !isScanning
                switchSimul.isEnabled = !isScanning
                switchServer.isEnabled = !isScanning
            }
        }

        // 🔹 Simul Mode 스위치 리스너 추가
        switchSimul.isChecked = useSimulatorMode
        switchSimul.setOnCheckedChangeListener { _, isChecked ->
            useSimulatorMode = isChecked
            val mode = if (isChecked) "Simulated" else "Real"
            clearDeviceList()
            Toast.makeText(this, "Mode: $mode", Toast.LENGTH_SHORT).show()
            if (!useSimulatorMode) {
                initData()
            }
        }

        // 🔹 Scan 버튼 클릭 리스너 (1회 스캔)
        btnScan.setOnClickListener {
            lifecycleScope.launch {
                bleManager.startScan(useSimulatorMode, useRemoteJson)
            }
        }

        // 🔹 n Scan 버튼 클릭 리스너 (반복 스캔)
        btnNScan.setOnClickListener {
            if (isScanning) {
                stopScanLoop()
            } else {
                if (deviceList.isNotEmpty()) {
                    clearDeviceList()
                }
                bleManager.startScanLoop(useSimulatorMode, useRemoteJson)
            }
        }

        // 🔹 Clear 버튼 클릭 리스너 (목록 초기화)
        btnClear.setOnClickListener {
            clearDeviceList()
        }
    }

    // 🔹 BLE 장치 클릭 시 실행할 동작 (MainActivity에서 직접 처리)
    private fun onDeviceSelected(serviceData: String) {
        if (isServerMode) {
            val serviceDataAscii = BLEUtils.hexToAscii(serviceData) ?: ""
            simulManager.fetchMemberInfo(serviceDataAscii) // 서버에서 멤버십 정보 조회
        } else {
            Toast.makeText(this, "Server Mode가 비활성화되어 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mac = bleManager.getDeviceMacAddress()
            val message = if (mac != null) {
                "Hello Beacon-$mac !"
            } else {
                "Failed to retrieve MAC address!"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔹 오류 다이얼로그 표시 함수
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("오류 발생")
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }


    // 🔹 n Scan 중지 (반복 스캔 종료)
    private fun stopScanLoop() {
        isScanning = false
        btnNScan.text = "n Scan"
        btnScan.isEnabled = true
        btnClear.isEnabled = true
        switchSimul.isEnabled = true
        switchServer.isEnabled = true
        scanJob?.cancel()

        Toast.makeText(this, "Scan Stopped", Toast.LENGTH_SHORT).show()
    }

    // 🔹 RecyclerView 목록 초기화
    private fun clearDeviceList() {
        deviceList.clear()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "목록이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
    }
}