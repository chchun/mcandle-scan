package com.mcandle.blescan

/**
 * BLE 및 시뮬레이션 데이터를 업데이트하기 위한 인터페이스
 */
interface DeviceUpdateListener {
    fun updateDeviceList(devices: List<DeviceModel>)
}

