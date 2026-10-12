package com.ihvpn.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val autoUpdate: Boolean = true,
    val updateIntervalHours: Int = 12,
    val lastUpdatedEpochMs: Long = 0L,
    val remainingBytes: Long? = null,
    val remainingDays: Long? = null,
    val profileCount: Int = 0
)
