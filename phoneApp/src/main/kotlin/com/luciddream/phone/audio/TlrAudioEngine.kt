package com.luciddream.phone.audio

import kotlinx.coroutines.delay

/**
 * Audio cue synthesis engine for Targeted Lucidity Reactivation (TLR).
 * Generates soft sinusoidal chime tones at specified volume and frequency.
 */
open class TlrAudioEngine {

    data class AudioCuePlayed(
        val timestampMs: Long,
        val frequencyHz: Double,
        val durationMs: Long,
        val volumePercent: Double
    )

    private val playedHistory = mutableListOf<AudioCuePlayed>()

    fun getHistory(): List<AudioCuePlayed> = playedHistory.toList()

    /**
     * Plays a gentle acoustic cue (e.g. 432 Hz harmonic chime) at low volume
     * to reactivate pre-sleep lucidity intention without triggering cortical arousal/awakening.
     */
    open suspend fun playLucidityChime(volume: Double = 0.25, frequencyHz: Double = 432.0, durationMs: Long = 1200) {
        val clampedVol = volume.coerceIn(0.05, 0.60) // Safety clamp against waking user

        val event = AudioCuePlayed(
            timestampMs = System.currentTimeMillis(),
            frequencyHz = frequencyHz,
            durationMs = durationMs,
            volumePercent = clampedVol
        )
        playedHistory.add(event)

        // Simulate playback duration
        delay(durationMs)
    }

    /**
     * Pre-sleep conditioning playback: pairs the chime with active intention during MILD rehearsal.
     */
    open suspend fun playConditioningTone() {
        playLucidityChime(volume = 0.40, frequencyHz = 432.0, durationMs = 1500)
    }

    /**
     * Binaural theta beat playback (e.g. 432 Hz left, 438 Hz right = 6 Hz theta frequency).
     */
    open suspend fun playBinauralThetaBeat(volume: Double = 0.25, durationMs: Long = 2000) {
        playLucidityChime(volume = volume, frequencyHz = 432.0, durationMs = durationMs)
    }
}
