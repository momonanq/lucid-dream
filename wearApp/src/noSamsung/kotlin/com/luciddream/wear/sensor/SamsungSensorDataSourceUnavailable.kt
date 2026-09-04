package com.luciddream.wear.sensor

import android.content.Context

/**
 * Stand-in used when the Samsung Health Sensor SDK is not present in `wearApp/libs/`.
 *
 * The SDK is proprietary and cannot live in a public repository, so CI and fresh clones compile
 * this file instead of the real integration. Returning null makes the absence explicit:
 * [SensorDataSourceFactory] falls back to the standard Wear OS source, which reports
 * [SourceFidelity.ANDROID_STANDARD_HR] and no inter-beat intervals, and the scoring engine drops
 * the HRV term accordingly.
 *
 * Download the SDK from https://developer.samsung.com/health/sensor to build the real source; see
 * docs/SAMSUNG_HEALTH_PARTNER_GUIDE.md.
 */
fun createSamsungSensorDataSource(context: Context): SensorDataSource? = null
