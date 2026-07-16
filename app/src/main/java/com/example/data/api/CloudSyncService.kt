package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CloudSyncService {
    @GET("workshops/shodoky999/jobs.json")
    suspend fun getCloudJobs(): Map<String, CloudJobDto>?

    @POST("workshops/shodoky999/jobs.json")
    suspend fun uploadJob(@Body job: CloudJobDto): Map<String, String>
}

object RetrofitClient {
    private const val BASE_URL = "https://ksl-mecanica-default-rtdb.firebaseio.com/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val cloudService: CloudSyncService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CloudSyncService::class.java)
    }
}
