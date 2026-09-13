package com.left.app.core.data

import com.left.app.core.database.dao.UserProfileDao
import com.left.app.core.database.entity.UserProfileEntity
import com.left.app.core.model.UserProfile
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Domain-level access to the local user profile. UI never touches the DAO (PRD §13). */
interface UserProfileRepository {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun getProfile(): UserProfile?
    suspend fun upsertProfile(name: String, currencyCode: String, locale: String)
    suspend fun deleteProfile()

    companion object {
        /** Single-profile local MVP: the one profile row always uses this id. */
        const val SINGLETON_PROFILE_ID: String = "local_user"
    }
}

@Singleton
class RoomUserProfileRepository @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val clock: Clock,
) : UserProfileRepository {

    override fun observeProfile(): Flow<UserProfile?> =
        userProfileDao.observeProfile().map { it?.toDomain() }

    override suspend fun getProfile(): UserProfile? = userProfileDao.getProfile()?.toDomain()

    override suspend fun upsertProfile(name: String, currencyCode: String, locale: String) {
        val now = Instant.now(clock)
        val existing = userProfileDao.getById(UserProfileRepository.SINGLETON_PROFILE_ID)
        userProfileDao.upsert(
            UserProfileEntity(
                id = UserProfileRepository.SINGLETON_PROFILE_ID,
                name = name,
                currencyCode = currencyCode,
                locale = locale,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun deleteProfile() = userProfileDao.deleteAll()

    private fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
        id = id,
        name = name,
        currencyCode = currencyCode,
        locale = locale,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
