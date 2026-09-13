package com.left.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.left.app.core.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/** DAO for the single-row local user profile. */
@Dao
interface UserProfileDao {

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = :id")
    suspend fun getById(id: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profile LIMIT 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}
