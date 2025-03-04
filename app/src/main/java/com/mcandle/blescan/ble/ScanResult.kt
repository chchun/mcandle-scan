package com.mcandle.blescan.ble

import org.json.JSONArray

data class ScanResult(
    val errorCode: Int,           // 🔹 에러 코드
    val errorMessage: String,     // 🔹 에러 메시지
    val jsonArray:JSONArray
 //   val deviceList: List<Device>  // 🔹 BLE 스캔된 디바이스 리스트
)