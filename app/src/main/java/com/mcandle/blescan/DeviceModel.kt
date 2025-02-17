package com.mcandle.blescan

import com.mcandle.blescan.utils.BLEUtils

data class DeviceModel(
    val name: String = "Unknown",
    val address: String = "Unknown",
    var rssi: Int = 0,
    var txPower: Int? = null,
    var connectable: Boolean = false,
    var bondState: String = "None",
    var manufacturerData: String? = null,  // 제조사별 데이터 (ASCII)
    var serviceUuids: String? = null,  // Advertise에서 제공하는 서비스 UUID 목록 (ASCII)
    var serviceData: String? = null,  // 특정 Service Data (ASCII)
    var rawData: String? = null  // 전체 광고 패킷의 원시 데이터 (ASCII)
) {
    /** ✅ ASCII → HEX 변환 (Manufacturer Data) */
    fun getManufacturerDataHex(): String? {
        return manufacturerData?.let { BLEUtils.asciiToHex(it) }
    }

    /** ✅ HEX → ASCII 변환 (Manufacturer Data) */
    fun getManufacturerDataAscii(): String? {
        return manufacturerData?.let { BLEUtils.hexToAscii(it) }
    }

    /** ✅ ASCII → HEX 변환 (Service Data) */
    fun getServiceDataHex(): String? {
        return serviceData?.let { BLEUtils.asciiToHex(it) }
    }

    /** ✅ HEX → ASCII 변환 (Service Data) */
    fun getServiceDataAscii(): String? {
        return serviceData?.let { BLEUtils.hexToAscii(it) }
    }
}
