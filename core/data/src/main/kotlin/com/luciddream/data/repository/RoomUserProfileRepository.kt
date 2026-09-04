package com.luciddream.data.repository

import com.luciddream.data.db.UserProfileDao
import com.luciddream.data.db.UserProfileEntity
import com.luciddream.model.CuePreference
import com.luciddream.model.LucidGoal
import com.luciddream.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUserProfileRepository(
    private val dao: UserProfileDao,
    private val userId: String = "default_user"
) : UserProfileRepository {

    override fun getUserProfile(): Flow<UserProfile> {
        return dao.getProfileFlow(userId).map { entity ->
            entity?.toDomain() ?: UserProfile(id = userId)
        }
    }

    override suspend fun updateProfile(profile: UserProfile) {
        dao.saveProfile(profile.copy(id = userId).toEntity())
    }

    private fun UserProfileEntity.toDomain(): UserProfile {
        return UserProfile(
            id = id,
            bedtimeTargetMinutes = bedtimeTargetMinutes,
            wakeTargetMinutes = wakeTargetMinutes,
            lucidGoal = runCatching { LucidGoal.valueOf(lucidGoal) }.getOrDefault(LucidGoal.MORE_LUCID_DREAMS),
            cuePreference = runCatching { CuePreference.valueOf(cuePreference) }.getOrDefault(CuePreference.VIBRATION_ONLY),
            watchModel = watchModel,
            baselineHeartRate = baselineHeartRate,
            baselineIbiVariance = baselineIbiVariance,
            preferredHapticIntensity = preferredHapticIntensity,
            maxCuesPerNight = maxCuesPerNight,
            cooldownMinutes = cooldownMinutes,
            earliestCueMinutesAfterOnset = earliestCueMinutesAfterOnset,
            confidenceThreshold = confidenceThreshold,
            calibrationNightsCompleted = calibrationNightsCompleted
        )
    }

    private fun UserProfile.toEntity(): UserProfileEntity {
        return UserProfileEntity(
            id = id,
            bedtimeTargetMinutes = bedtimeTargetMinutes,
            wakeTargetMinutes = wakeTargetMinutes,
            lucidGoal = lucidGoal.name,
            cuePreference = cuePreference.name,
            watchModel = watchModel,
            baselineHeartRate = baselineHeartRate,
            baselineIbiVariance = baselineIbiVariance,
            preferredHapticIntensity = preferredHapticIntensity,
            maxCuesPerNight = maxCuesPerNight,
            cooldownMinutes = cooldownMinutes,
            earliestCueMinutesAfterOnset = earliestCueMinutesAfterOnset,
            confidenceThreshold = confidenceThreshold,
            calibrationNightsCompleted = calibrationNightsCompleted
        )
    }
}
