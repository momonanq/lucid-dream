plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

// The Samsung Health Sensor SDK is proprietary and cannot be committed to a public repository,
// so it is absent on CI and on any fresh clone. Its integration therefore lives in its own source
// directory, swapped for a stub when the AAR is missing: both provide createSamsungSensorDataSource,
// so the module compiles either way and CI stays green without the SDK.
// Download it from https://developer.samsung.com/health/sensor and drop the AAR into wearApp/libs/.
val samsungSensorAar: File? = fileTree("libs") { include("samsung-health-sensor-api-*.aar") }
    .files
    .minByOrNull { it.name }
val hasSamsungSensorSdk = samsungSensorAar != null

android {
    namespace = "com.luciddream.wear"
    compileSdk = 35

    sourceSets.getByName("main") {
        java.srcDir(if (hasSamsungSensorSdk) "src/samsung/kotlin" else "src/noSamsung/kotlin")
    }

    defaultConfig {
        applicationId = "com.luciddream.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    if (samsungSensorAar != null) {
        logger.lifecycle("Samsung Health Sensor SDK found: ${samsungSensorAar.name}")
        implementation(files(samsungSensorAar))
    } else {
        logger.lifecycle("Samsung Health Sensor SDK absent — building with the standard Wear OS sensor source only")
    }

    implementation(project(":core:model"))
    implementation(project(":core:algorithm"))
    implementation(project(":core:data"))

    val coroutinesVersion = "1.10.1"
    val serializationVersion = "1.8.0"
    val wearComposeVersion = "1.4.1"

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Wear OS Compose
    implementation("androidx.wear.compose:compose-material:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-foundation:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-navigation:$wearComposeVersion")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear:wear-ongoing:1.1.0")

    // Play Services Wearable
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.core:core-ktx:1.15.0")

    constraints {
        // androidx.activity's ActivityResult APIs are incompatible with fragment < 1.3.0, which
        // something in the transitive graph still resolves (lint: InvalidFragmentVersionForActivityResult).
        // A constraint raises that version only if fragment is already present, rather than
        // pulling in a dependency this Compose-only module does not otherwise use.
        implementation("androidx.fragment:fragment:1.8.5") {
            because("androidx.activity ActivityResult APIs require fragment 1.3.0 or newer")
        }
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}
