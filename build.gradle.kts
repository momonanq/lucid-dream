plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    dependencies {
        val coroutinesVersion = "1.10.1"
        val serializationVersion = "1.8.0"
        val datetimeVersion = "0.6.1"
        val junitVersion = "5.11.4"

        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
        "implementation"("org.jetbrains.kotlinx:kotlinx-datetime:$datetimeVersion")

        "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
        "testImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
