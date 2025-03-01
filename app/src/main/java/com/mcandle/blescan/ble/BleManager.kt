package com.mcandle.blescan.ble

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import vpos.apipackage.At
import java.io.InputStreamReader
import com.mcandle.blescan.DeviceModel
import com.mcandle.blescan.utils.BLEUtils
import java.net.URL
import kotlin.random.Random

class BleManager(private val context: Context) {
    private val deviceList = mutableListOf<DeviceModel>()

    // 🔹 UI 업데이트를 위한 콜백 (MainActivity에서 설정)
    private var updateListener: ((List<DeviceModel>) -> Unit)? = null
    private var scanStatusListener: ((Boolean) -> Unit)? = null  // 🔹 UI에서 스캔 상태 변경을 위한 콜백
    private var scanJob: Job? = null
    private var isScanning = false  // 🔹 이제 BleManager에서 관리

    fun setUpdateListener(listener: (List<DeviceModel>) -> Unit) {
        this.updateListener = listener
    }

    fun setScanStatusListener(listener: (Boolean) -> Unit) {
        this.scanStatusListener = listener
    }


    companion object {
        @Volatile
        private var instance: BleManager? = null

        fun getInstance(context: Context): BleManager {
            return instance ?: synchronized(this) {
                instance ?: BleManager(context).also { instance = it }
            }
        }
    }

    suspend fun getDeviceMacAddress(): String? {
        return withContext(Dispatchers.IO) {
            val macAddress = arrayOfNulls<String>(1)
            val ret = At.Lib_GetAtMac(macAddress)
            if (ret == 0) macAddress[0] else null
        }
    }

    suspend fun startScan(isSimulated: Boolean, useRemoteJson: Boolean = true) {
        withContext(Dispatchers.IO) {
            Log.d("BLE_SCAN", "Starting BLE scan... Simulate Mode: ${if (isSimulated) "ON" else "OFF"}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Starting BLE scan... Simulate Mode: ${if (isSimulated) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }

            if (isSimulated) {
                delay(2000)
                val simulatedJson = generateDeviceJson(useRemoteJson)
                val simulatedDevices = parseDevice(simulatedJson)
                withContext(Dispatchers.Main) {
                    updateDeviceList(simulatedDevices)
                }
            } else {
                vposStartScan()
            }
        }
    }

    fun startScanLoop(isSimulated: Boolean, useRemoteJson: Boolean) {
        if (isScanning) return  // 이미 실행 중이면 중복 실행 방지

        isScanning = true
        scanStatusListener?.invoke(true)  // 🔹 UI에서 버튼 상태 변경하도록 콜백 실행

        // 🔹 UI에서 Toast 메시지 표시 (MainActivity에서 처리)
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "Continuous Scan Started", Toast.LENGTH_SHORT).show()
        }

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isScanning) {
                startScan(isSimulated, useRemoteJson)
                delay(5000)
            }
        }
    }

    fun stopScanLoop() {
        isScanning = false
        scanJob?.cancel()
        scanStatusListener?.invoke(false)  // 🔹 UI에서 버튼 상태 변경하도록 콜백 실행

        // 🔹 UI에서 Toast 메시지 표시 (MainActivity에서 처리)
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "Scan Stopped", Toast.LENGTH_SHORT).show()
        }
    }

    fun isCurrentlyScanning(): Boolean {
        return isScanning
    }

    private fun vposStartScan() {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = getScanSettings()
            val ret = At.Lib_AtStartNewScan(settings.macAddress, settings.broadcastName, -settings.rssi, settings.manufacturerId, settings.data)
            withContext(Dispatchers.Main) {
                if (ret == 0) {
                    Toast.makeText(context, "Scanning started successfully", Toast.LENGTH_SHORT).show()
                    startReceivingData()
                } else {
                    Toast.makeText(context, "Error while scanning, ret = $ret", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startReceivingData() {
        CoroutineScope(Dispatchers.IO).launch {
            recvScanData()
        }
    }

    private fun getScanSettings(): ScanSettings {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("scanInfo", Context.MODE_PRIVATE)
        return ScanSettings(
            sharedPreferences.getString("macAddress", "") ?: "",
            sharedPreferences.getString("broadcastName", "") ?: "",
            sharedPreferences.getString("rssi", "0")?.toIntOrNull() ?: 0,
            sharedPreferences.getString("manufacturerId", "") ?: "",
            sharedPreferences.getString("data", "") ?: ""
        )
    }

    private fun generateDeviceJson(useRemoteJson: Boolean = true): List<String> {
        val gson = Gson()
        val deviceList = mutableListOf<JsonObject>()
        val useRemoteJson = true

        return try {
            if (useRemoteJson) {
                // 🔹 Render 서버에서 JSON 데이터 가져오기
                val url = "https://json-render-d4wv.onrender.com/devices.json"
                val jsonString = URL(url).readText()

                val jsonArray = gson.fromJson(jsonString, JsonArray::class.java)
                for (jsonElement in jsonArray) {
                    if (jsonElement is JsonObject) {
                        deviceList.add(jsonElement)
                    }
                }
            } else {
                // 🔹 assets 폴더에서 devices.json 로드
                val inputStream = context.applicationContext.assets.open("devices.json")
                val reader = InputStreamReader(inputStream)
                val jsonArray = gson.fromJson(reader, JsonArray::class.java)

                for (jsonElement in jsonArray) {
                    if (jsonElement is JsonObject) {
                        deviceList.add(jsonElement)
                    }
                }
            }

            // 🔹 랜덤으로 장치 개수 선택 (1~5개)
            deviceList.shuffled().take(Random.nextInt(1, 6)).map { jsonObject ->
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
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // JSON 문자열을 DeviceModel 리스트로 변환하는 함수
    fun parseDevice(simulatedJson: List<String>): List<DeviceModel> {
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
                //val serviceUuids = serviceUuidsHex?.let { BLEUtils.hexToAscii(it) }
                val serviceUuids = serviceUuidsHex
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

    fun getDeviceList(): List<DeviceModel> {
        return deviceList.toList()
    }

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

            // 신규 및 업데이트된 장치 처리 (HEX 값 그대로 유지)
            for (newDevice in newDevices) {
                val existingDevice = deviceList.find { it.address == newDevice.address }

                if (existingDevice != null) {
                    existingDevice.rssi = newDevice.rssi
                    existingDevice.manufacturerData = newDevice.manufacturerData  // ✅ 변환 제거
                    existingDevice.serviceData = newDevice.serviceData  // ✅ 변환 제거
                    updatedDeviceCount++
                } else {
                    deviceList.add(newDevice) // ✅ 그대로 추가
                    newDeviceCount++
                }
            }

            // 🔹 MainActivity에 데이터 변경 알림 (UI 업데이트는 MainActivity에서)
            updateListener?.invoke(deviceList)

            Log.d("BLE_SCAN", "Updated Device List: New = $newDeviceCount, Updated = $updatedDeviceCount")
        }
    }

    private suspend fun recvScanData() {
        val recvData = ByteArray(2048)
        val recvDataLen = IntArray(2)

        while (true) {
            val ret = At.Lib_ComRecvAT(recvData, recvDataLen, 20, 1000)
            if (ret < 0) {
                Log.e("Scan", "Failed to receive data")
                continue
            }

            val buffer = String(recvData, Charsets.UTF_8)
            Log.d("BleManager", "Received Data: $buffer")

            // 데이터를 JSON으로 변환하여 파싱
            val deviceMap = mutableMapOf<String, JsonObject>()
            val lines = buffer.split("\r\n", "\r", "\n")

            for (line in lines) {
                if (line.startsWith("MAC:")) {
                    val parts = line.split(",")

                    val mac = parts[0].split(":")[1].trim()
                    val rssi = parts[1].split(":")[1].trim().toIntOrNull() ?: -100
                    val payload = parts[2].split(":")[1].trim()

                    val deviceJson = deviceMap.getOrPut(mac) { JsonObject().apply { addProperty("MAC", mac) } }

                    if (parts[2].startsWith("RSP")) {
                        deviceJson.addProperty("RSP_org", payload)
                        deviceJson.add("RSP", parseAdvertisementData(payload))
                    } else if (parts[2].startsWith("ADV")) {
                        deviceJson.addProperty("ADV_org", payload)
                        deviceJson.add("ADV", parseAdvertisementData(payload))
                    }

                    deviceJson.addProperty("RSSI", rssi)
                    deviceJson.addProperty("Timestamp", System.currentTimeMillis())
                }
            }

            // JSON 변환 후 DeviceModel 리스트 생성 및 업데이트
            val jsonDevices = deviceMap.values.mapNotNull { parseJsonToDeviceModel(it) }
            withContext(Dispatchers.Main) {
                updateDeviceList(jsonDevices)
            }
        }
    }

    // 🔹 광고 데이터 파싱 함수 (BeaconActivity.java 기반)
    private fun parseAdvertisementData(payload: String): JsonObject {
        val parsedData = JsonObject()
        val byteArray = hexStringToByteArray(payload)
        var offset = 0

        while (offset < byteArray.size) {
            val length = byteArray[offset++].toInt() and 0xFF
            if (length == 0) break

            val type = byteArray[offset].toInt() and 0xFF
            offset++

            val data = byteArray.copyOfRange(offset, offset + length - 1)
            offset += length - 1

            when (type) {
                0x01 -> parsedData.addProperty("Flags", bytesToHex(data))
                0x02, 0x03, 0x04, 0x05, 0x06, 0x07 -> parsedData.addProperty("Service UUIDs", bytesToHex(data))
                0x08, 0x09 -> parsedData.addProperty("Device Name", String(data))
                0x0A -> parsedData.addProperty("TX Power Level", data[0].toInt())
                0xFF -> parsedData.addProperty("Manufacturer Data", bytesToHex(data))
                else -> parsedData.addProperty("Unknown Data ($type)", bytesToHex(data))
            }
        }
        return parsedData
    }

    // 🔹 JSON을 DeviceModel로 변환
    private fun parseJsonToDeviceModel(json: JsonObject): DeviceModel? {
        return try {
            val macAddress = json["MAC"]?.asString ?: return null
            val rssi = json["RSSI"]?.asInt ?: -100
            val timestamp = json["Timestamp"]?.asLong ?: System.currentTimeMillis()

            val adv = json["ADV"]?.asJsonObject
            val rsp = json["RSP"]?.asJsonObject

            val deviceName = adv?.get("Device Name")?.asString ?: rsp?.get("Device Name")?.asString ?: "Unknown"
            val serviceUuids = adv?.get("Service UUIDs")?.asString ?: rsp?.get("Service UUIDs")?.asString
            val manufacturerData = adv?.get("Manufacturer Data")?.asString ?: rsp?.get("Manufacturer Data")?.asString

            DeviceModel(
                name = deviceName,
                address = macAddress,
                rssi = rssi,
                txPower = null,
                manufacturerData = manufacturerData,
                serviceUuids = serviceUuids,
                serviceData = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private fun hexStringToByteArray(hex: String): ByteArray {
        // 1. HEX 문자열 필터링 (유효한 16진수 문자만 남김)
        val cleanedHex = hex.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }

        // 2. 길이가 짝수가 아닌 경우 패딩 추가 (예외 방지)
        val validHex = if (cleanedHex.length % 2 != 0) "0$cleanedHex" else cleanedHex

        return try {
            val len = validHex.length
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((validHex[i].digitToInt(16) shl 4) + validHex[i + 1].digitToInt(16)).toByte()
            }
            data
        } catch (e: Exception) {
            Log.e("BLE_ERROR", "Invalid HEX string: $hex", e)
            byteArrayOf() // 예외 발생 시 빈 배열 반환
        }
    }

    // 🔹 바이트 배열을 헥스 문자열로 변환
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

}

data class ScanSettings(
    val macAddress: String,
    val broadcastName: String,
    val rssi: Int,
    val manufacturerId: String,
    val data: String
)
