package com.mcandle.blescan.utils

object BLEUtils {
    /** ✅ ASCII 문자열을 HEX 문자열로 변환 */
    fun asciiToHex(ascii: String): String {
        return ascii.toByteArray(Charsets.UTF_8)
            .joinToString(" ") { String.format("%02X", it) }
    }

    /** ✅ HEX 문자열을 ASCII 문자열로 변환 (오류 방지 추가) */
    fun hexToAscii(hex: String): String {
        return try {
            // 🔹 HEX 데이터가 올바른지 검증
            if (!hex.matches(Regex("^[0-9A-Fa-f ]+\$"))) {
                return hex // 변환하지 않고 그대로 반환 (예: "")
            }

            hex.split(" ")
                .filter { it.isNotEmpty() }
                .map { it.toInt(16).toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            hex // 변환 실패 시 원본 HEX 문자열 반환
        }
    }

    // Hex 문자열을 바이트 배열로 변환하는 함수
    private fun hexStringToByteArray(hexString: String): ByteArray {
        return hexString.split(" ")
            .filter { it.isNotEmpty() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
