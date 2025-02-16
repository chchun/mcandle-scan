package com.mcandle.blescan

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface MembershipApiService {
    @GET("get")
    fun getMemberInfo(@Query("id") id: String): Call<MemberInfo>
}
