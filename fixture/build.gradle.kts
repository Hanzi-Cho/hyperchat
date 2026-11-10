plugins { id("com.android.application") }
android {
    namespace = "dev.hanzicho.hyperchat.fixture"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.hanzicho.hyperchat.fixture"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "dev.hanzicho.hyperchat.fixture.HyperchatProbe"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
