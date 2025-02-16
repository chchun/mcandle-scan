package com.mcandle.blescan

data class DeviceModel(
    val name: String = "Unknown",
    val address: String = "Unknown",
    var rssi: Int = 0,
    var txPower: Int? = null,
    var connectable: Boolean = false,
    var bondState: String = "None",
    var manufacturerData: Map<Int, ByteArray>? = null,  // 제조사별 데이터
    var serviceUuids: List<String>? = null,  // Advertise에서 제공하는 서비스 UUID 목록
    var serviceData: Map<String, ByteArray>? = null,  // 특정 서비스 UUID의 데이터
    var rawData: ByteArray? = null  // 전체 광고 패킷의 원시 데이터
)
