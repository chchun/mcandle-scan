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
import com.mcandle.blescan.ui.MemberInfoDialog
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.random.Random
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BLEDeviceAdapter
    private lateinit var btnScan: Button
    private lateinit var btnNScan: Button
    private lateinit var btnClear: Button
    private lateinit var switchSimul: Switch
    private lateinit var switchServer: Switch

    private var isScanning = false
    private var scanJob: Job? = null
    private val deviceList = mutableListOf<DeviceModel>()
    private var useSimulatorMode = true  // 시뮬레이션 모드 사용 여부
    private var isServerMode = true      // 서버 모드 사용 여부

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

        // 🔹 Simul Mode 스위치 리스너 추가
        switchSimul.isChecked = useSimulatorMode
        switchSimul.setOnCheckedChangeListener { _, isChecked ->
            useSimulatorMode = isChecked
            val mode = if (isChecked) "Simulated" else "Real"
            clearDeviceList()
            Toast.makeText(this, "Mode: $mode", Toast.LENGTH_SHORT).show()
        }

        // 🔹 Scan 버튼 클릭 리스너 (1회 스캔)
        btnScan.setOnClickListener {
            startScan(useSimulatorMode)

        }

        // 🔹 n Scan 버튼 클릭 리스너 (반복 스캔)
        btnNScan.setOnClickListener {
            if (isScanning) {
                stopScanLoop()
            } else {
                if (deviceList.isNotEmpty()) {
                    clearDeviceList()
                }
                startScanLoop(useSimulatorMode)
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
              fetchMemberInfo(serviceData) // 서버에서 멤버십 정보 조회
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

    // 🔹 BLE 실제 스캔 함수 (BLE 장치 검색)
    private fun startScan(isSimulated: Boolean) {
        Log.d("BLE_SCAN", "Starting BLE scan... Simulate Mode: ${if (isSimulated) "ON" else "OFF"}")
        runOnUiThread {
            Toast.makeText(this, "Starting BLE scan... Simulate Mode: ${if (isSimulated) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        if (isSimulated) {
            lifecycleScope.launch(Dispatchers.IO) {
                delay(2000)
                val simulatedJson = generateDeviceJson()
                val simulatedDevices = parseDevice(simulatedJson)
                updateDeviceList(simulatedDevices)
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                delay(3000)
                val newDevice = DeviceModel(
                    name = "BLE Device ${Random.nextInt(100, 999)}",
                    address = "00:11:22:33:${Random.nextInt(10, 99)}",
                    rssi = Random.nextInt(-100, -50)
                )
                updateDeviceList(listOf(newDevice))
            }
        }

    }

    // 🔹 n Scan 실행 (반복 스캔)
    private fun startScanLoop(isSimulated: Boolean) {
        isScanning = true
        btnNScan.text = "Stop"
        btnScan.isEnabled = false
        btnClear.isEnabled = false
        switchSimul.isEnabled = false
        switchServer.isEnabled = false

        // 🔹 Toast를 UI 스레드에서 실행하도록 변경
        runOnUiThread {
            Toast.makeText(this, "Continuous Scan Started", Toast.LENGTH_SHORT).show()
        }
        scanJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isScanning) {
                startScan(isSimulated)
                delay(5000)
            }
        }
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

    // 🔹 RecyclerView 목록 업데이트 함수
    private suspend fun updateDeviceList(newDevices: List<DeviceModel>) {
        withContext(Dispatchers.Main) {
            var newDeviceCount = 0
            var updatedDeviceCount = 0

            // 기존 장치 중에서 이번 스캔에 포함되지 않은 장치를 비활성화 (RSSI -100)
            for (device in deviceList) {
                if (newDevices.none { it.address == device.address }) {
                    device.rssi = -100
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
            Log.d("BLE_SCAN", "Updated Device List: New = $newDeviceCount, Updated = $updatedDeviceCount")
        }
    }

    // 🔹 RecyclerView 목록 초기화
    private fun clearDeviceList() {
        deviceList.clear()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "목록이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // JSON 문자열을 DeviceModel 리스트로 변환하는 함수
    private fun parseDevice(simulatedJson: List<String>): List<DeviceModel> {
        val gson = Gson()
        val deviceList = mutableListOf<DeviceModel>()

        for (jsonString in simulatedJson) {
            try {
                val jsonObject = JsonParser().parse(jsonString).asJsonObject

                val macAddress = jsonObject.get("MAC")?.asString ?: "Unknown"
                val rssi = jsonObject.get("RSSI")?.asInt ?: -100
                val txPower = jsonObject.get("TX Power Level")?.asInt

                val advObject = jsonObject.getAsJsonObject("ADV")

                val deviceName = advObject?.get("Device Name")?.asString ?: "Unknown"
                val manufacturerData = advObject?.get("Manufacturer Data")?.asString
                val serviceUuids = advObject?.get("Service UUIDs")?.asString
                val serviceData = advObject?.get("Service Data")?.asString

                // DeviceModel 객체 생성 및 리스트 추가
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

    // Hex 문자열을 바이트 배열로 변환하는 함수
    private fun hexStringToByteArray(hexString: String): ByteArray {
        return hexString.split(" ")
            .filter { it.isNotEmpty() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // JSON 기반 가짜 BLE 장치 데이터 생성
    private fun generateDeviceJson(): List<String> {
        val gson = Gson()

        val bleDataList = listOf(
            """
        {
          "MAC": "1E:95:39:87:B7:66",
          "ADV_org": "1EFF06000010F202281F45B31190C69A607079F44C6B77555B0B7811159036D",
          "ADV": {
            "Manufacturer Data": "06 00 01 0F 20 22 81 F4 5B 31 19 0C 69 A6 07 07 9F 44 C6 B7 75 55 B0 B7 81 11 59 03 6D"
          },
          "RSSI": "-71",
          "TX Power Level": "-10",
          "Timestamp": 1739263607589
        }
        """.trimIndent(),
            """
        {
          "MAC": "Z4:4D:BD:F2:AD:36",
          "ADV_org": "02010613095641524C30363432303330331303030320303A00203FF0201",
          "ADV": {
            "Flags": "06 ",
            "Device Name": "VARL063030010002",
            "Service UUIDs": "A0 02 ",
            "Service Data": "chchun",
            "Manufacturer Data": "02 01 "
          },
          "RSSI": "-75",
          "TX Power Level": "-15",
          "Timestamp": 1739263607592
        }
        """.trimIndent(),
            """
        {
          "MAC": "A1:B2:C3:D4:E5:F6",
          "ADV_org": "1EFF0201060302AABBCCDDEE",
          "ADV": {
            "Manufacturer Data": "02 01 AA BB CC DD EE"
          },
          "RSSI": "-60",
          "TX Power Level": "-5",
          "Timestamp": 1739263607600
        }
        """.trimIndent(),
            """
        {
          "MAC": "B2:C3:D4:E5:F6:A1",
          "ADV_org": "0201060503CCDD0011223344",
          "ADV": {
            "Flags": "05",
            "Device Name": "Smart Sensor",
            "Manufacturer Data": "CC DD 00 11 22 33 44"
          },
          "RSSI": "-68",
          "TX Power Level": "-12",
          "Timestamp": 1739263607610
        }
        """.trimIndent(),
            """
        {
          "MAC": "C3:D4:E5:F6:A1:B2",
          "ADV_org": "1EFF06010A0B0C0D0E0F",
          "ADV": {
            "Manufacturer Data": "06 01 0A 0B 0C 0D 0E 0F"
          },
          "RSSI": "-55",
          "TX Power Level": "-20",
          "Timestamp": 1739263607620
        }
        """.trimIndent(),
            """
        {
          "MAC": "D4:E5:F6:A1:B2:C3",
          "ADV_org": "0201060906AABBCCDDEEFF",
          "ADV": {
            "Flags": "09",
            "Device Name": "BLE Tracker",
            "Service UUIDs": "CC DD EE FF",
            
            "Manufacturer Data": "AA BB CC DD EE FF"
          },
          "RSSI": "-50",
          "TX Power Level": "-18",
          "Timestamp": 1739263607630
        }
        """.trimIndent(),
            """
        {
          "MAC": "E5:F6:A1:B2:C3:D4",
          "ADV_org": "1EFF020106000A0B0C0D",
          "ADV": {
            "Manufacturer Data": "02 01 06 00 0A 0B 0C 0D"
          },
          "RSSI": "-85",
          "TX Power Level": "-25",
          "Timestamp": 1739263607640
        }
        """.trimIndent(),
            """
        {
          "MAC": "F6:A1:B2:C3:D4:E5",
          "ADV_org": "0201061234AABBCCDDEEFFFF",
          "ADV": {
            "Flags": "12",
            "Device Name": "Smart Lock",
            "Service UUIDs": "AA BB CC DD EE FF FF",
            "Service data": "test",
            "Manufacturer Data": "FF FF 00 11 22 33 44"
          },
          "RSSI": "-73",
          "TX Power Level": "-7",
          "Timestamp": 1739263607650
        }
        """.trimIndent(),
            """
        {
          "MAC": "A1:A2:A3:A4:A5:A6",
          "ADV_org": "1EFF02010B0C0D0E0F",
          "ADV": {
            "Manufacturer Data": "02 01 0B 0C 0D 0E 0F"
          },
          "RSSI": "-69",
          "TX Power Level": "-22",
          "Timestamp": 1739263607660
        }
        """.trimIndent(),
            """
        {
          "MAC": "B2:B3:B4:B5:B6:B7",
          "ADV_org": "0201061415161718191A1B1C1D",
          "ADV": {
            "Flags": "14",
            "Device Name": "Smart Plug",
            "Service UUIDs": "15 16 17 18 19 1A 1B 1C 1D",
            "Manufacturer Data": "1E 1F 20 21 22 23 24 25"
          },
          "RSSI": "-80",
          "TX Power Level": "-27",
          "Timestamp": 1739263607670
        }
        """.trimIndent()
        )

        // 10개 중 랜덤으로 1~6개 선택하여 RSSI 및 TX Power 값을 변경
        return bleDataList.shuffled().take(Random.nextInt(1, 6)).map { jsonString ->
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

            // RSSI 및 TX Power Level을 랜덤 값으로 설정
            jsonObject.addProperty("RSSI", Random.nextInt(-100, -50))
            jsonObject.addProperty("TX Power Level", Random.nextInt(-30, 0))

            gson.toJson(jsonObject)
        }
    }
}
