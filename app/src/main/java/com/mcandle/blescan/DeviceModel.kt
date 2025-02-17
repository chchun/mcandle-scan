package com.mcandle.blescan

data class DeviceModel(
    val name: String = "Unknown",
    val address: String = "Unknown",
    var rssi: Int = 0,
    var txPower: Int? = null,
    var connectable: Boolean = false,
    var bondState: String = "None",
    var manufacturerData: String? = null,  // 제조사별 데이터
    var serviceUuids: String? = null,  // Advertise에서 제공하는 서비스 UUID 목록
    var serviceData: String? = null,  // 특정 서비스 UUID의 데이터
    var rawData: String? = null  // 전체 광고 패킷의 원시 데이터
)
