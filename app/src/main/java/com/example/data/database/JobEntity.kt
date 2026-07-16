package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val clientName: String,
    val description: String,
    val totalPrice: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "Cotizado", "Pagado (VISA)", "Pagado (PayPal)", "Pagado (Cripto)"
    val paymentMethod: String = "", // "VISA", "PAYPAL", "BTC", "ETH", "USDT"
    val referenceCode: String = "", // Bank reference or TX Hash
    val isSync: Boolean = false
)
