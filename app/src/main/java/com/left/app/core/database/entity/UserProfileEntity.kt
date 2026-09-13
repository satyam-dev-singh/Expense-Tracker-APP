package com.left.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for the local user profile (table `user_profile`).
 * Single-row table in the local MVP — see UserProfileRepository.SINGLETON_PROFILE_ID.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val locale: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
