plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.marketdata"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.okhttp.core)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
