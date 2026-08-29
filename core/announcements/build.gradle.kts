plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.announcements"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // api rather than implementation: `AppResult` and `ErrorKind` are in this module's own
    // signatures, so every caller needs them on its classpath to read what the gateway returned.
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:network"))
    implementation(libs.retrofit.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The gateway test builds a real Retrofit over an interceptor that answers without a socket,
    // so the assertion covers the address that is actually requested — path, prefix and query —
    // rather than a constant this module also wrote. A path typo is precisely the mistake that
    // asserting a constant against itself cannot catch.
    testImplementation(libs.okhttp.core)
    testImplementation(libs.retrofit.gson)
    testImplementation(libs.gson)
}
