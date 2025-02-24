package com.mcandle.blescan

import android.content.Context
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
import com.mcandle.blescan.ui.MemberInfoDialog
import com.mcandle.blescan.utils.BLEUtils
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.random.Random
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader

import vpos.apipackage.At
import vpos.apipackage.Beacon

import com.mcandle.blescan.ble.BleManager

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

    private fun initData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mac = arrayOf("")
            val ret = At.Lib_GetAtMac(mac)
            val message = if (ret == 0) {
                "Hello Beacon-${mac[0]} !"
            } else {
                "Lib_GetAtMac Error : $ret!"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔹 BLE 장치 클릭 시 실행할 동작 (MainActivity에서 직접 처리)
    private fun onDeviceSelected(serviceData: String) {
        if (isServerMode) {
            val serviceData_ascii = serviceData.let { BLEUtils.hexToAscii(it) } ?: ""
            fetchMemberInfo(serviceData_ascii) // 서버에서 멤버십 정보 조회
        } else {
            Toast.makeText(this, "Server Mode가 비활성화되어 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔹 서버에서 멤버십 정보 가져오는 함수
    private fun fetchMemberInfo(deviceId: String) {
        val apiService = RetrofitClient.instance.create(MembershipApiService::class.java)
        apiService.getMemberInfo(deviceId).enqueue(object : Callback<MemberInfo> {
            override fun onResponse(call: Call<MemberInfo>, response: Response<MemberInfo>) {
                if (response.isSuccessful && response.body() != null) {
                    val memberInfo = response.body()!!
                    val dialog = MemberInfoDialog(memberInfo)
                    dialog.show(supportFragmentManager, "MemberInfoDialog")
                } else {
                    showErrorDialog("${deviceId} 님의 정보를 조회할 수 없습니다.")
                }
            }

            override fun onFailure(call: Call<MemberInfo>, t: Throwable) {
                showErrorDialog("네트워크 오류 또는 서버 응답이 없습니다.")
            }
        })
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

    // JSON 문자열을 DeviceModel 리스트로 변환하는 함수
    private fun parseDevice(simulatedJson: List<String>): List<DeviceModel> {
        val deviceList = mutableListOf<DeviceModel>()

        for (jsonString in simulatedJson) {
            try {
                val jsonObject = JsonParser().parse(jsonString).asJsonObject

                val macAddress = jsonObject.get("MAC")?.asString ?: "Unknown"
                val rssi = jsonObject.get("RSSI")?.asInt ?: -100
                val txPower = jsonObject.get("TX Power Level")?.asInt

                val advObject = jsonObject.getAsJsonObject("ADV")

                val deviceName = advObject?.get("Device Name")?.asString ?: "Unknown"
                val manufacturerDataHex = advObject?.get("Manufacturer Data")?.asString
                val serviceUuidsHex = advObject?.get("Service UUIDs")?.asString
                val serviceDataHex = advObject?.get("Service Data")?.asString

                // 🔹 HEX → ASCII 변환 적용
                val manufacturerData = manufacturerDataHex?.let { BLEUtils.hexToAscii(it) }
                val serviceUuids = serviceUuidsHex?.let { BLEUtils.hexToAscii(it) }
                val serviceData = serviceDataHex?.let { BLEUtils.hexToAscii(it) }

                // DeviceModel 생성 및 리스트 추가
                deviceList.add(
                    DeviceModel(
                        name = deviceName,
                        address = macAddress,
                        rssi = rssi,
                        txPower = txPower,
                        manufacturerData = manufacturerData,
                        serviceUuids = serviceUuids,
                        serviceData = serviceData
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return deviceList
    }

    private fun generateDeviceJson(context: Context): List<String> {
        val gson = Gson()
        val deviceList = mutableListOf<JsonObject>()

        try {
            // 🔹 assets에서 devices.json 읽기
            val inputStream = context.assets.open("devices.json")
            val reader = InputStreamReader(inputStream)
            val jsonArray = gson.fromJson(reader, JsonArray::class.java)

            for (jsonElement in jsonArray) {
                if (jsonElement is JsonObject) {
                    deviceList.add(jsonElement)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 🔹 랜덤으로 장치 개수 선택 (1~5개)
        return deviceList.shuffled().take(Random.nextInt(1, 6)).map { jsonObject ->
            // 🔹 RSSI 및 TX Power Level을 랜덤 값으로 설정
            jsonObject.addProperty("RSSI", Random.nextInt(-100, -50))
            jsonObject.addProperty("TX Power Level", Random.nextInt(-30, 0))

            // 🔹 Service Data 및 Manufacturer Data를 HEX로 변환
            jsonObject.getAsJsonObject("ADV")?.apply {
                get("Service Data")?.asString?.let {
                    addProperty("Service Data", BLEUtils.asciiToHex(it))
                }
                get("Manufacturer Data")?.asString?.let {
                    addProperty("Manufacturer Data", BLEUtils.asciiToHex(it))
                }
            }

            gson.toJson(jsonObject)
        }
    }

    private fun vpos_startScan() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sharedPreferences = getSharedPreferences("scanInfo", MODE_PRIVATE)
            val macAddress = sharedPreferences.getString("macAddress", "")
            val broadcastName = sharedPreferences.getString("broadcastName", "")
            val rssi = sharedPreferences.getString("rssi", "0")?.toIntOrNull() ?: 0
            val manufacturerId = sharedPreferences.getString("manufacturerId", "")
            val data = sharedPreferences.getString("data", "")

            Log.e("TAG", "Start Scan with: MAC=$macAddress, Name=$broadcastName, RSSI=$rssi, Manufacturer=$manufacturerId, Data=$data")

            val ret = At.Lib_AtStartNewScan(macAddress, broadcastName, -rssi, manufacturerId, data)

            withContext(Dispatchers.Main) {
                if (ret == 0) {
                    Toast.makeText(this@MainActivity, "Scanning started successfully", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch(Dispatchers.IO) { recvScanData() }
                } else {
                    Toast.makeText(this@MainActivity, "Error while scanning, ret = $ret", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private suspend fun recvScanData() {
        val recvData = ByteArray(2048)
        val recvDataLen = IntArray(2)

        while (isScanning) {
            val ret = At.Lib_ComRecvAT(recvData, recvDataLen, 20, 1000)
            if (ret < 0) {
                Log.e("Scan", "Failed to receive data")
                continue
            }

            val buffer = String(recvData, Charsets.UTF_8)
            val dataList = buffer.split("\r\n", "\r", "\n")

            val deviceList = mutableListOf<DeviceModel>()

            for (line in dataList) {
                if (line.startsWith("MAC:")) {
                    val parts = line.split(",")
                    val mac = parts[0].split(":")[1]
                    val rssi = parts[1].split(":")[1].toInt()
                    val payload = parts[2].split(":")[1]

                    val device = DeviceModel(
                        name = "Unknown",
                        address = mac,
                        rssi = rssi,
                        serviceData = payload
                    )

                    deviceList.add(device)
                }
            }

            withContext(Dispatchers.Main) {
                //updateDeviceList(deviceList)
            }
        }
    }



}