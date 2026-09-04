plugins {
    application
}

application {
    mainClass.set("com.luciddream.phone.MainKt")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:algorithm"))
    implementation(project(":core:data"))
    implementation(project(":wearApp"))
}
