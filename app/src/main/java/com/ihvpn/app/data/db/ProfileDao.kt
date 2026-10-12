package com.ihvpn.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE subscriptionId = :subscriptionId ORDER BY sortOrder ASC")
    fun observeForSubscription(subscriptionId: String): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE subscriptionId IS NULL ORDER BY sortOrder ASC")
    fun observeManual(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE subscriptionId IS NULL ORDER BY sortOrder ASC")
    suspend fun getManualOnce(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelected(): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("UPDATE profiles SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE profiles SET isSelected = 1 WHERE id = :id")
    suspend fun select(id: String)

    @Query("UPDATE profiles SET pingMs = :pingMs, lastValidatedEpochMs = :ts, isValid = :isValid WHERE id = :id")
    suspend fun updatePingResult(id: String, pingMs: Int?, ts: Long, isValid: Boolean)

    @Query("UPDATE profiles SET totalDownBytes = totalDownBytes + :down, totalUpBytes = totalUpBytes + :up WHERE id = :id")
    suspend fun addTraffic(id: String, down: Long, up: Long)

    @Query("UPDATE profiles SET totalDownBytes = 0, totalUpBytes = 0")
    suspend fun resetAllCounters()

    @Query("UPDATE profiles SET totalDownBytes = 0, totalUpBytes = 0 WHERE subscriptionId = :subscriptionId")
    suspend fun resetCountersForSubscription(subscriptionId: String)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE subscriptionId = :subscriptionId")
    suspend fun deleteForSubscription(subscriptionId: String)
}
