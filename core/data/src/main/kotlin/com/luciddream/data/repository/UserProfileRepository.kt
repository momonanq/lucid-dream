package com.luciddream.data.repository

import com.luciddream.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface UserProfileRepository {
    fun getUserProfile(): Flow<UserProfile>
    suspend fun updateProfile(profile: UserProfile)
}

class InMemoryUserProfileRepository(
    initialProfile: UserProfile = UserProfile()
) : UserProfileRepository {
    private val profileState = MutableStateFlow(initialProfile)

    override fun getUserProfile(): Flow<UserProfile> = profileState.asStateFlow()

    override suspend fun updateProfile(profile: UserProfile) {
        profileState.value = profile
    }
}
