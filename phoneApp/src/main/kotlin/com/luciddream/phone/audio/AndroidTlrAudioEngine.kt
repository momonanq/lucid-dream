package com.luciddream.phone.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Android implementation of TLR Audio Engine synthesizing harmonic sine chimes
 * and binaural theta beats directly using PCM AudioTrack with anti-startle envelopes.
 */
class AndroidTlrAudioEngine : TlrAudioEngine() {

    private val sampleRate = 44100

    override suspend fun playLucidityChime(
        volume: Double,
        frequencyHz: Double,
        durationMs: Long
    ) = withContext(Dispatchers.Default) {
        val clampedVol = volume.coerceIn(0.05, 0.60)
        val numSamples = (durationMs * sampleRate / 1000).toInt()
        val buffer = ShortArray(numSamples)

        val attackSamples = (numSamples * 0.20).toInt().coerceAtLeast(1)
        val decaySamples = (numSamples * 0.35).toInt().coerceAtLeast(1)
        val sustainEnd = numSamples - decaySamples

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = when {
                i < attackSamples -> i.toDouble() / attackSamples
                i > sustainEnd -> (numSamples - i).toDouble() / decaySamples
                else -> 1.0
            }
            val sample = sin(2.0 * PI * frequencyHz * t) * envelope * clampedVol * Short.MAX_VALUE
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        playPcmMono(buffer)
        super.playLucidityChime(volume, frequencyHz, durationMs)
    }

    override suspend fun playBinauralThetaBeat(
        volume: Double,
        durationMs: Long
    ) = withContext(Dispatchers.Default) {
        val clampedVol = volume.coerceIn(0.05, 0.50)
        val baseHz = 432.0
        val beatHz = 6.0 // 6 Hz Theta
        val leftHz = baseHz
        val rightHz = baseHz + beatHz

        val numFrames = (durationMs * sampleRate / 1000).toInt()
        val buffer = ShortArray(numFrames * 2) // Stereo: left, right interleaved

        val attackFrames = (numFrames * 0.15).toInt().coerceAtLeast(1)
        val decayFrames = (numFrames * 0.25).toInt().coerceAtLeast(1)
        val sustainEnd = numFrames - decayFrames

        for (i in 0 until numFrames) {
            val t = i.toDouble() / sampleRate
            val envelope = when {
                i < attackFrames -> i.toDouble() / attackFrames
                i > sustainEnd -> (numFrames - i).toDouble() / decayFrames
                else -> 1.0
            }

            val leftSample = sin(2.0 * PI * leftHz * t) * envelope * clampedVol * Short.MAX_VALUE
            val rightSample = sin(2.0 * PI * rightHz * t) * envelope * clampedVol * Short.MAX_VALUE

            buffer[i * 2] = leftSample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i * 2 + 1] = rightSample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        playPcmStereo(buffer)
        super.playBinauralThetaBeat(volume, durationMs)
    }

    private fun playPcmMono(buffer: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
        } catch (e: Exception) {
            // Audio hardware fallback
        }
    }

    private fun playPcmStereo(buffer: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
        } catch (e: Exception) {
            // Audio hardware fallback
        }
    }
}
