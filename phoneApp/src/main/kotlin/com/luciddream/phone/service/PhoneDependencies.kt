package com.luciddream.phone.service

import android.content.Context
import com.luciddream.data.db.LucidDatabase
import com.luciddream.data.repository.RoomNightSessionRepository
import com.luciddream.data.repository.RoomUserProfileRepository
import com.luciddream.data.samsung.MockSamsungHealthDataGateway
import com.luciddream.phone.audio.AndroidTlrAudioEngine

/**
 * Service locator and dependency provider for phone application runtime.
 */
object PhoneDependencies {

    @Volatile
    private var coordinator: PhoneSessionCoordinator? = null

    fun getCoordinator(context: Context): PhoneSessionCoordinator {
        return coordinator ?: synchronized(this) {
            val db = LucidDatabase.getInstance(context.applicationContext)
            val sessionRepo = RoomNightSessionRepository(db.nightSessionDao())
            val profileRepo = RoomUserProfileRepository(db.userProfileDao())
            val samsungGateway = MockSamsungHealthDataGateway()
            val audioEngine = AndroidTlrAudioEngine()

            PhoneSessionCoordinator(
                sessionRepository = sessionRepo,
                profileRepository = profileRepo,
                samsungHealthGateway = samsungGateway,
                audioElement = audioEngine
            ).also { coordinator = it }
        }
    }
}
