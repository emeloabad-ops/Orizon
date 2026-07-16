package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CloudJobDto(
    val uuid: String,
    val clientName: String,
    val description: String,
    val totalPrice: Double,
    val timestamp: Long,
    val status: String
)
