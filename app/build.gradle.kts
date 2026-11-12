plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val liteRtLmVersion = "0.19.1"

android {
    namespace = "dev.hanzicho.hyperchat"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.hanzicho.hyperchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk.abiFilters.addAll(setOf("arm64-v8a", "x86_64"))
        buildConfigField("String", "LITERT_LM_VERSION", "\"$liteRtLmVersion\"")
    }
    buildFeatures.buildConfig = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes.addAll(setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*"))
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmVersion")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
