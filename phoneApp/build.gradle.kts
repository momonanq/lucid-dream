plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

android {
    namespace = "com.luciddream.phone"
    compileSdk = 35

    defaultConfig {
        // Must be identical to the wearApp applicationId. Google Play services delivers Data Layer
        // traffic only between apps whose package name AND signature match across the two devices,
        // so a differing id silently breaks MessageClient, CapabilityClient and the offline queue:
        // the phone and the watch never see each other.
        // https://developer.android.com/training/wearables/data/overview#security
        // The namespace above stays distinct — it only names generated classes.
        applicationId = "com.luciddream"
        minSdk = 30
        targetSdk = 35
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
    implementation(project(":core:model"))
    implementation(project(":core:algorithm"))
    implementation(project(":core:data"))
    // Direct dependency on :wearApp is eliminated in favor of Wearable Data Layer!

    val coroutinesVersion = "1.10.1"
    val serializationVersion = "1.8.0"

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Jetpack Compose & Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Google Play Services Wearable
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
