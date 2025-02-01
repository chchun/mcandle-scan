package com.example.mybleapp

import android.util.Log
import kotlinx.coroutines.*
import vpos.apipackage.At

class BLEScanner {

    private val deviceList = mutableListOf<String>() // BLE 장치 정보를 저장할 리스트
    private var isScanning = false // 스캔 상태 플래그

    /**
     * BLE 스캔 시작 (비동기)
     */
    fun startScan(scanDuration: Int = 10, callback: (List<String>) -> Unit) {
        if (isScanning) {
            Log.d("BLEScanner", "Scanning is already in progress.")
            return
        }

        Log.d("BLEScanner", "Starting BLE Scan...")
        val scanResult = At.Lib_AtStartScan(scanDuration)
        if (scanResult != 0) {
            Log.e("BLEScanner", "Error while starting scan, return code = $scanResult")
            return
        }

        isScanning = true

        CoroutineScope(Dispatchers.IO).launch {
            val devices = Array(20) { "" }

            for (i in 0 until scanDuration / 2) { // 2초 간격으로 확인
                delay(2000) // 2초 대기
                val ret = At.Lib_GetScanResult(3, devices) // 3초 동안 장치 검색
                if (ret == 0) {
                    for (device in devices) {
                        if (!device.isNullOrEmpty() && !deviceList.contains(device)) {
                            Log.d("BLEScanner", "NEW DEVICE DISCOVERED: $device")
                            deviceList.add(device)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        callback(deviceList) // UI에 업데이트 콜백 실행
                    }
                }
            }
            isScanning = false
        }
    }

    /**
     * 현재 검색된 BLE 장치 리스트 반환
     */
    fun getScanResults(): List<String> {
        return deviceList
    }

    /**
     * BLE 스캔 중지
     */
    fun stopScan() {
        if (isScanning) {
            Log.d("BLEScanner", "Stopping BLE Scan...")
            isScanning = false
        }
    }
}
