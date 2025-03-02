package com.mcandle.blescan

import android.os.Bundle
import android.util.Log
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

class MainActivity : AppCompatActivity(){
    private lateinit var recyclerView: RecyclerView

    private lateinit var btnScan: Button
    private lateinit var btnNScan: Button
    private lateinit var btnClear: Button
    private lateinit var switchSimul: Switch
    private lateinit var switchServer: Switch

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

        // 🔹 RecyclerView Adapter 초기화 (클릭 이벤트 MainActivity에서 처리)
        adapter = BLEDeviceAdapter(deviceList, ::onDeviceSelected)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 🔹 BleManager가 데이터를 업데이트하면 UI 갱신
        bleManager.setUpdateListener { newDevices ->
            runOnUiThread {
                deviceList.clear()
                deviceList.addAll(newDevices)
                adapter.notifyDataSetChanged()  // ✅ UI 업데이트는 MainActivity에서 수행
            }
        }
        // 🔹 Server 모드 스위치 리스너 추가
        switchServer.setOnCheckedChangeListener { _, isChecked ->
            isServerMode = isChecked
            val mode = if (isChecked) "Server Mode Enabled" else "Server Mode Disabled"
            Toast.makeText(this, mode, Toast.LENGTH_SHORT).show()
        }

        // 🔹 Simul 상태 변경 시 UI 업데이트 리스너 설정
        bleManager.setScanStatusListener  { isScanningState ->
            runOnUiThread {
                val scanning = bleManager.isCurrentlyScanning() // 🔹 현재 스캔 상태 확인
                btnScan.text = if (scanning) "Stop" else "Scan"
                btnNScan.isEnabled = !scanning
                btnClear.isEnabled = !scanning
                switchSimul.isEnabled = !scanning
                switchServer.isEnabled = !scanning
            }
        }
        // 🔹 Simul 상태 변경 시 UI 업데이트 리스너 설정
        bleManager.setSimulStatusListener { isScanningState ->
            runOnUiThread {
                val scanning = bleManager.isCurrentlyScanning() // 🔹 현재 스캔 상태 확인
                btnNScan.text = if (scanning) "Stop" else "Simul"
                btnScan.isEnabled = !scanning
                btnClear.isEnabled = !scanning
                switchSimul.isEnabled = !scanning
                switchServer.isEnabled = !scanning
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
            if (bleManager.isCurrentlyScanning()) {
                bleManager.stopScan()
            } else {
                clearDeviceList()
                bleManager.startScan(useRemoteJson)
            }
        }

        // 🔹 n Scan 버튼 클릭 리스너 (반복 스캔)
        btnNScan.setOnClickListener {
            if (bleManager.isCurrentlyScanning()) {
                bleManager.stopScanLoop()
            } else {
                clearDeviceList()
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
        lifecycleScope.launch {
            // ✅ IO 스레드에서 Master 모드 설정 및 MAC 주소 가져오기
            val (result, mac) = withContext(Dispatchers.IO) {
                val result = bleManager.enableMasterMode(true)

                val mac = bleManager.getDeviceMacAddress()  // ✅ MAC 주소 가져오기
                result to mac // ✅ 두 값을 Pair로 반환
            }

            // ✅ 로그 출력은 IO 스레드에서 수행
            if (result == 0) {
                Log.d("MAIN", "Master mode enabled successfully")
            } else {
                Log.e("MAIN", "Failed to enable Master mode, error code: $result")
            }

            // ✅ UI에서 메시지 출력
            val message = if (mac != null) {
                "Hello Beacon-$mac !"
            } else {
                "Failed to retrieve MAC address!"
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
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

    // 🔹 RecyclerView 목록 초기화
    private fun clearDeviceList() {
        deviceList.clear()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "목록이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
    }

}