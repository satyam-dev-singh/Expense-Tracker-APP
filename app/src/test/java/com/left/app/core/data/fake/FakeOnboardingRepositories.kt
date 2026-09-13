package com.left.app.core.data.fake

import com.left.app.core.data.IncomeSourceRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.datastore.OnboardingPreferences
import com.left.app.core.model.IncomeFrequency
import com.left.app.core.model.IncomeSource
import com.left.app.core.model.UserProfile
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Additional fakes for onboarding tests. Each accepts an optional shared
 * [events] list so tests can assert write ORDERING — the completion flag must
 * always be written last (see CompleteOnboarding).
 */
class FakeUserProfileRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val events: MutableList<String>? = null,
) : UserProfileRepository {

    private val profile = MutableStateFlow<UserProfile?>(null)

    override fun observeProfile(): Flow<UserProfile?> = profile

    override suspend fun getProfile(): UserProfile? = profile.value

    override suspend fun upsertProfile(name: String, currencyCode: String, locale: String) {
        events?.add("profile")
        val now = Instant.now(clock)
        val existing = profile.value
        profile.value = UserProfile(
            id = UserProfileRepository.SINGLETON_PROFILE_ID,
            name = name,
            currencyCode = currencyCode,
            locale = locale,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    override suspend fun deleteProfile() {
        profile.value = null
    }
}

class FakeIncomeSourceRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val events: MutableList<String>? = null,
) : IncomeSourceRepository {

    private val sources = MutableStateFlow<List<IncomeSource>>(emptyList())

    override fun observeAll(): Flow<List<IncomeSource>> = sources

    override fun observeActive(): Flow<List<IncomeSource>> =
        sources.map { list -> list.filter { it.active } }

    override suspend fun getById(id: String): IncomeSource? =
        sources.value.firstOrNull { it.id == id }

    override suspend fun create(
        name: String,
        amount: Money,
        frequency: IncomeFrequency,
        nextDate: LocalDate?,
        active: Boolean,
    ): IncomeSource {
        events?.add("income")
        val now = Instant.now(clock)
        val source = IncomeSource(
            id = UUID.randomUUID().toString(),
            name = name,
            amount = amount,
            frequency = frequency,
            nextDate = nextDate,
            active = active,
            createdAt = now,
            updatedAt = now,
        )
        sources.update { it + source }
        return source
    }

    override suspend fun update(incomeSource: IncomeSource) {
        sources.update { list -> list.map { if (it.id == incomeSource.id) incomeSource else it } }
    }

    override suspend fun setActive(id: String, active: Boolean) {
        sources.update { list -> list.map { if (it.id == id) it.copy(active = active) else it } }
    }

    override suspend fun delete(id: String) {
        sources.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeOnboardingPreferences(
    private val events: MutableList<String>? = null,
) : OnboardingPreferences {

    var completed: Boolean = false
        private set
    var currencyCode: String? = null
        private set

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        events?.add("completed-flag")
        this.completed = completed
    }

    override suspend fun setDefaultCurrencyCode(code: String) {
        events?.add("currency-flag")
        this.currencyCode = code
    }
}
