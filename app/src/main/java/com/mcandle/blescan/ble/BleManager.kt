package com.mcandle.blescan.ble

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.HashMap


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
    private var isMaster: Boolean = true // 🔹 Master/Beacon 모드 상태 변수 추가

    // 🔹 UI 업데이트를 위한 콜백 (MainActivity에서 설정)
    private var updateListener: ((List<DeviceModel>) -> Unit)? = null
    private var scanStatusListener: ((Boolean) -> Unit)? = null  // 🔹 UI에서 스캔 상태 변경을 위한 콜백
    private var SimulStatusListener: ((Boolean) -> Unit)? = null  // 🔹 UI에서 스캔 상태 변경을 위한 콜백
    private var scanJob: Job? = null
    private var isScanning = false  // 🔹 이제 BleManager에서 관리

    fun setUpdateListener(listener: (List<DeviceModel>) -> Unit) {
        this.updateListener = listener
    }

    fun setSimulStatusListener(listener: (Boolean) -> Unit) {
        this.SimulStatusListener = listener
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

    private fun hexStringToByteArray(hexString: String): ByteArray {
        var len = hexString.length
        if (len % 2 == 1) len--
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((hexString[i].digitToIntOrNull(16) ?: -1 shl 4)
            + hexString[i + 1].digitToIntOrNull(16)!! ?: -1).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString()
    }

    private fun bytesToHex(bytes: ByteArray, len: Int): String {
        val sb = StringBuilder()
        for (i in 0 until len) {
            sb.append(String.format("%02X", bytes[i]))
        }
        return sb.toString()
    }

    suspend fun getDeviceMacAddress(): String? {
        return withContext(Dispatchers.IO) {
            val macAddress = arrayOfNulls<String>(1)
            val ret = At.Lib_GetAtMac(macAddress)
            if (ret == 0) macAddress[0] else null
        }
    }

    suspend fun enableMasterMode(enable: Boolean): Int {
        return withContext(Dispatchers.IO) {
            if (isMaster == enable) {
                Log.d("BLE_MANAGER", "Already in the requested mode. No changes made.")
                return@withContext 0 // ✅ 올바른 방식
            }

            val ret = At.Lib_EnableMaster(enable)
            return@withContext ret // ✅ 올바른 방식
        }
    }

    fun startScan(useRemoteJson: Boolean = true) {
        isScanning = true
        CoroutineScope(Dispatchers.Main).launch {
            scanStatusListener?.invoke(true) // ✅ UI 스레드에서 실행
        }
        val sp: SharedPreferences = context.getSharedPreferences("scanInfo", Context.MODE_PRIVATE)
        Log.e("TAG", "startScan: macAddress: ${sp.getString("macAddress", "")}")
        Log.e("TAG", "startScan: broadcastName: ${sp.getString("broadcastName", "")}")
        Log.e("TAG", "startScan: rssi: ${sp.getString("rssi", "")}")
        Log.e("TAG", "startScan: manufacturerId: ${sp.getString("manufacturerId", "")}")
        Log.e("TAG", "startScan: data: ${sp.getString("data", "")}")

        var ret = At.Lib_AtStartNewScan(
            sp.getString("macAddress", ""),
            sp.getString("broadcastName", ""),
            -sp.getString("rssi", "0")!!.toInt(),
            sp.getString("manufacturerId", ""),
            sp.getString("data", "")
        )

        if (ret == 0) {
            isScanning = true
            CoroutineScope(Dispatchers.IO).launch {
                recvScanData()
            }
        }

        if (ret == 0) {
            SendPromptMsg("NEW DEVICE DISCOVERED\n")
        } else {
            SendPromptMsg("ERROR WHILE SCANNING, RET = $ret\n")
        }
    }

    fun startScanLoop(isSimulated: Boolean, useRemoteJson: Boolean) {
        if (isScanning) return  // 이미 실행 중이면 중복 실행 방지

        isScanning = true
        CoroutineScope(Dispatchers.Main).launch {
            SimulStatusListener?.invoke(true) // ✅ UI 스레드에서 실행
        }

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d("BLE_SCAN", "Starting BLE scan... Simulate Mode: ${if (isSimulated) "ON" else "OFF"}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Starting BLE scan... Simulate Mode: ${if (isSimulated) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }

            while (isScanning) {
                val simulatedJson = generateDeviceJson(useRemoteJson)
                val simulatedDevices = parseDevice(simulatedJson)

                withContext(Dispatchers.Main) {
                    updateDeviceList(simulatedDevices)
                }
                delay(2000)
            }
        }
    }

    // BleManager.kt
    fun stopScan() {
        if (!isScanning) return // ✅ 이미 중지 상태면 실행하지 않음.
        isScanning = false

        CoroutineScope(Dispatchers.Main).launch {
            SimulStatusListener?.invoke(false)  // 🔹 UI 상태 업데이트 보장
            Toast.makeText(context, "Scan Stopped", Toast.LENGTH_SHORT).show()
        }

        // 🔹 데이터 초기화
        deviceList.clear()
        updateListener?.invoke(emptyList())

        Log.d("BLE_SCAN", "Scan loop stopped.")
    }

    fun stopScanLoop() {
        if (!isScanning) return // ✅ 이미 중지 상태면 실행하지 않음.

        isScanning = false
        scanJob?.cancel()
        scanJob = null

        CoroutineScope(Dispatchers.Main).launch {
            SimulStatusListener?.invoke(false)  // 🔹 UI 상태 업데이트 보장
            Toast.makeText(context, "Scan Stopped", Toast.LENGTH_SHORT).show()
        }

        // 🔹 데이터 초기화
        deviceList.clear()
        updateListener?.invoke(emptyList())

        Log.d("BLE_SCAN", "Scan loop stopped.")
    }

    fun isCurrentlyScanning(): Boolean {
        return isScanning && scanJob?.isActive == true
    }

    private fun vposStartScan() {
        val sp: SharedPreferences = context.getSharedPreferences("scanInfo", Context.MODE_PRIVATE)
        Log.e("TAG", "startScan: macAddress: ${sp.getString("macAddress", "")}")
        Log.e("TAG", "startScan: broadcastName: ${sp.getString("broadcastName", "")}")
        Log.e("TAG", "startScan: rssi: ${sp.getString("rssi", "")}")
        Log.e("TAG", "startScan: manufacturerId: ${sp.getString("manufacturerId", "")}")
        Log.e("TAG", "startScan: data: ${sp.getString("data", "")}")

        var ret = At.Lib_AtStartNewScan(
            sp.getString("macAddress", ""),
            sp.getString("broadcastName", ""),
            -sp.getString("rssi", "0")!!.toInt(),
            sp.getString("manufacturerId", ""),
            sp.getString("data", "")
        )

        if (ret == 0) {
            isScanning = true
            CoroutineScope(Dispatchers.IO).launch {
                recvScanData()
            }
        }

        if (ret == 0) {
            SendPromptMsg("NEW DEVICE DISCOVERED\n")
        } else {
            SendPromptMsg("ERROR WHILE SCANNING, RET = $ret\n")
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

    @Throws(JSONException::class)
    fun parseAdvertisementData(advertisementData: ByteArray): JSONObject? {
//        Map<String, String> parsedData = new HashMap<>();
//        byte[] advertisementData =new byte[advertiseData.length()/2];
        val parsedData = JSONObject()
        var offset = 0
        while (offset < advertisementData.size) {
            val length = advertisementData[offset++].toInt() and 0xFF
            if (length == 0) break

            val type = advertisementData[offset].toInt() and 0xFF
            offset++

            val data = ByteArray(length - 1)
            if (length - 1 > advertisementData.size - offset)  //data format issue.
            {
                return null
            }
            System.arraycopy(advertisementData, offset, data, 0, length - 1)
            offset += length - 1

            when (type) {
                0x01 -> parsedData.put("Flags", bytesToHex(data))
                0x02, 0x03 -> parsedData.put("Service UUIDs", bytesToHex(data))
                0x04, 0x05 -> parsedData.put("Service UUIDs", bytesToHex(data))
                0x06, 0x07 -> parsedData.put("Service UUIDs", bytesToHex(data))
                0x08, 0x09 -> parsedData.put("Device Name", String(data))
                0x0A -> //                    byte [] tx_power=hexStringToByteArray(new String(data));
                    parsedData.put("TX Power Level", data[0].toInt())

                0xFF -> parsedData.put("Manufacturer Data", bytesToHex(data))
                else -> parsedData.put("Unknown Data ($type)", bytesToHex(data))
            }
        }

        return parsedData
    }

    private fun parsePayload(payload: String): JSONObject? {
        val result = JSONObject()
        var index = 0

        while (index < payload.length) {
            // ��������Ƿ�Խ��
            if (index + 2 > payload.length) {
                break
            }
            val length = payload.substring(index, index + 2).toInt(16)
            index += 2

            // ��������Ƿ�Խ��
            if (index + 2 > payload.length) {
                break
            }
            val type = payload.substring(index, index + 2).toInt(16)
            index += 2

            // ��������Ƿ�Խ��
            if (index + length * 2 > payload.length) {
                break
            }
            val data = payload.substring(index, index + length * 2)
            index += length * 2

            try {
                result.put("Type $type", data)
            } catch (e: JSONException) {
                Log.e("TAG", "parsePayload:Type " + e.message)
                //            throw new RuntimeException(e);
                return null
            }
        }

        return result
    }

    private suspend fun recvScanData() {
        withContext(Dispatchers.IO) {
            val recvData = ByteArray(2048)
            val recvDataLen = IntArray(2)
            var lineLeft: String = ""

            while (isScanning) {
                val ret = At.Lib_ComRecvAT(recvData, recvDataLen, 20, 1000)
                Log.e("TAG", "runLib_ComRecvAT: recvDataLen" + recvDataLen[0])
                Log.e(
                    "TAG", "Lib_ComRecvAT recvData: " + bytesToHex(
                        recvData,
                        recvDataLen.getOrNull(0) ?: 0  // 🚀 null-safe 처리
                    )
                )
                val deviceMap: MutableMap<String, JSONObject?> = HashMap()
                var startProcessing = false
                // String buff= lineLeft+new String(recvData);
                val buff = lineLeft + String(recvData, 0, recvDataLen[0])
                // String []data=buff.split("\r\n|\r|\n");
                val data = buff.split("\\r\\n|\\r|\\n".toRegex()).toTypedArray()
                //Log.e("TAG", "debug crash position:echo21" );
                val lineCount = data.size
                // if(lineCount>0)//each time response data left last line ,for maybe data not recv all.
                //     lineLeft = data[lineCount-1];
                // else
                //     lineLeft="";

                lineLeft = if ((data.size > 0)) data[data.size - 1] else ""
                //for (String line : data)
                for (i in 0 until lineCount - 1) {
                    val line = data[i]
                    //                    Log.e("TAG", "debug crash position:echo22" );
                    if (line.startsWith("MAC:")) {
                        startProcessing = true
                        val parts = line.split(",".toRegex(), limit = 3).toTypedArray()
                        if (parts.size < 3) {
                            continue
                        }

                        val mac = parts[0].split(":".toRegex(), limit = 2)
                            .toTypedArray()[1].trim { it <= ' ' }
                        val rssi = parts[1].split(":".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()[1].trim { it <= ' ' }
                        var irssi = 0
                        try {
                            irssi = rssi.toInt() // ��֤ RSSI �Ƿ�Ϊ��Ч����
                        } catch (e: NumberFormatException) {
                            Log.e("TAG", "Invalid RSSI value: $rssi")
                            continue
                        }
                        val payload = parts[2].split(":".toRegex(), limit = 2)
                            .toTypedArray()[1].trim { it <= ' ' }
                        if ((payload.length > 62) || (payload.length % 2 != 0)) continue
                        //                        Log.e("TAG", "debug crash position:echo20" );
                        var device: JSONObject?
                        if (deviceMap.containsKey(mac)) {
                            device = deviceMap[mac]
                        } else {
                            device = JSONObject()
                            try {
                                device.put("MAC", mac)
                            } catch (e: JSONException) {
                                Log.e(
                                    "TAG",
                                    "Handler runLib_ComRecvAT mac 0000: JSONException$e"
                                )
                                //throw new RuntimeException(e);
                                continue
                            }
                            deviceMap[mac] = device
                        }
                        //                        Log.e("TAG", "debug crash position:echo19" );
                        if (parts[2].startsWith("RSP")) {
                            try {
                                checkNotNull(device)
                                device.put("RSP_org", payload)
                                device.put(
                                    "RSP",
                                    parseAdvertisementData(hexStringToByteArray(payload))
                                )
                            } catch (e: JSONException) {
                                Log.e("TAG", "Runnable 444: JSONException$e")
                                //                                throw new RuntimeException(e);
                                continue
                            }
                        } else if (parts[2].startsWith("ADV")) {
                            //device.put("ADV", parsePayload(payload));
                            try {
                                checkNotNull(device)
                                device.put("ADV_org", payload)
                                device.put(
                                    "ADV",
                                    parseAdvertisementData(hexStringToByteArray(payload))
                                )
                            } catch (e: JSONException) {
                                Log.e("TAG", "Runnable 333: JSONException$e")
                                //                                throw new RuntimeException(e);
                                continue
                            }
                        }
                        //Log.e("TAG", "debug crash position:echo18" );
                        try {
                            checkNotNull(device)
                            // Log.e("TAG", "debug crash position:echo18"+rssi );
                            device.put("RSSI", irssi)
                        } catch (e: JSONException) {
                            Log.e("TAG", "Runnable 222: JSONException" + e.message)
                            //                            throw new RuntimeException(e);
                            continue
                        }
                        //                        Log.e("TAG", "debug crash position:echo17" );
                        // ���ʱ����ֶ�
                        try {
                            //                                long curr_time=System.currentTimeMillis();
                            device.put("Timestamp", System.currentTimeMillis())
                        } catch (e: JSONException) {
                            //Log.e("TAG", "Runnable 000: JSONException"+e );
                            //                            throw new RuntimeException(e);
                            continue
                        }
                        //                        Log.e("TAG", "debug crash position:echo16" );
                    } else if (startProcessing) {
                        // ����Ѿ���ʼ����MAC���ݣ���������MAC��ͷ�����ݣ�������
                        continue
                    }
                    //                    Log.e("TAG", "debug crash position:echo14---"+);
                }

                val jsonArray = JSONArray(deviceMap.values)

                // JSON 변환 후 DeviceModel 리스트 생성 및 업데이트
                //val jsonDevices = deviceMap.values.mapNotNull { parseJsonToDeviceModel(it) }
                //withContext(Dispatchers.Main) {
                //    updateDeviceList(jsonDevices)
                //}
            }
        }
    }
}
private fun SendPromptMsg(msg: String) {
    Log.e("TAG", "UI Message: $msg") // UI 메시지를 로그로 출력
}

private fun SendPromptScanMsg(msg: String) {
    Log.e("TAG", "Scan Data: $msg") // UI 메시지를 로그로 출력
}


data class ScanSettings(
    val macAddress: String,
    val broadcastName: String,
    val rssi: Int,
    val manufacturerId: String,
    val data: String
)
