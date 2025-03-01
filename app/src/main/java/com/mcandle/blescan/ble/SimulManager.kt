package com.mcandle.blescan.ble

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.mcandle.blescan.ui.MemberInfoDialog
import com.mcandle.blescan.MembershipApiService
import com.mcandle.blescan.RetrofitClient
import com.mcandle.blescan.MemberInfo
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SimulManager(private val context: Context) {
    companion object {
        @Volatile
        private var instance: SimulManager? = null

        fun getInstance(context: Context): SimulManager {
            return instance ?: synchronized(this) {
                instance ?: SimulManager(context).also { instance = it }
            }
        }
    }

    fun fetchMemberInfo(deviceId: String) {
        val apiService = RetrofitClient.instance.create(MembershipApiService::class.java)
        apiService.getMemberInfo(deviceId).enqueue(object : Callback<MemberInfo> {
            override fun onResponse(call: Call<MemberInfo>, response: Response<MemberInfo>) {
                if (response.isSuccessful && response.body() != null) {
                    val dialog = MemberInfoDialog(response.body()!!)
                    dialog.show((context as androidx.appcompat.app.AppCompatActivity).supportFragmentManager, "MemberInfoDialog")
                } else {
                    showErrorDialog("${deviceId} 님의 정보를 조회할 수 없습니다.")
                }
            }

            override fun onFailure(call: Call<MemberInfo>, t: Throwable) {
                showErrorDialog("네트워크 오류 또는 서버 응답이 없습니다.")
            }
        })
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(context)
            .setTitle("오류 발생")
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }
}
